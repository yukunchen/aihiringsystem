#!/bin/bash
# Discord orchestrator-queue watcher.
#
# Polls /opt/ai-hiring/notifications/orchestrator-queue.jsonl every
# POLL_INTERVAL seconds, POSTs any entry that has not yet been pushed
# to Discord, and marks it as `pushed_discord: true`.
#
# Independence from the Claude hook: we set a separate field
# (`pushed_discord`) rather than mutating `read`, so the orchestrator
# Claude session's UserPromptSubmit hook can still pick up the same
# entry on the user's next prompt. Discord is an out-of-band push;
# the hook is an in-band context injection. Both are useful.
#
# Secret: DISCORD_WEBHOOK_URL must be set (via /etc/ai-hiring/discord.env
# or systemd EnvironmentFile). The URL is NEVER logged.
#
# Env knobs:
#   POLL_INTERVAL   seconds between scans (default 5)
#   QUEUE_PATH      override orchestrator queue path (default below)
#
# Exits non-zero only on config errors; runtime errors are logged and
# retried on the next poll.
set -u

QUEUE_PATH="${QUEUE_PATH:-/opt/ai-hiring/notifications/orchestrator-queue.jsonl}"
POLL_INTERVAL="${POLL_INTERVAL:-5}"
ENV_FILE="${ENV_FILE:-/etc/ai-hiring/discord.env}"

if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    set -a; source "$ENV_FILE"; set +a
fi

if [ -z "${DISCORD_WEBHOOK_URL:-}" ]; then
    echo "FATAL: DISCORD_WEBHOOK_URL not set (expected in $ENV_FILE or env)" >&2
    exit 2
fi

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

log "discord-notify-watcher starting; queue=$QUEUE_PATH interval=${POLL_INTERVAL}s"

# Process one queue scan. Reads all entries, finds those without
# pushed_discord=true AND that are orchestrator-targeted, POSTs each,
# then rewrites the file with pushed_discord flags updated. Skips
# gracefully if the file is absent.
scan_and_push() {
    [ -f "$QUEUE_PATH" ] || return 0

    python3 - "$QUEUE_PATH" "$DISCORD_WEBHOOK_URL" <<'PYEOF'
import json, os, sys, urllib.request, urllib.error

path, webhook = sys.argv[1], sys.argv[2]

try:
    with open(path) as f:
        raw = f.readlines()
except FileNotFoundError:
    sys.exit(0)

entries = []
for line in raw:
    stripped = line.strip()
    if not stripped:
        entries.append(None)  # preserve blank lines on write-back
        continue
    try:
        entries.append(json.loads(stripped))
    except json.JSONDecodeError:
        entries.append({'__raw__': stripped})

def should_push(e):
    if not isinstance(e, dict) or '__raw__' in e:
        return False
    if e.get('pushed_discord'):
        return False
    # Only push orchestrator-facing notifications.
    if e.get('target') == 'orchestrator':
        return True
    if e.get('type') == 'pr_ready':
        return True
    return False

TYPE_COLORS = {
    'pr_ready': 0x3498db,   # blue
    'deploy_fail': 0xe74c3c, # red
    'deploy': 0x2ecc71,      # green
}

def post(e):
    title = e.get('message', '(no title)')
    # Details often include Author/Branch/URL lines plus a body preview.
    # Send as embed; cap description at 1800 chars (Discord limit 4096).
    desc = (e.get('details') or '')[:1800]
    run_url = e.get('run_url') or ''
    color = TYPE_COLORS.get(e.get('type', ''), 0x95a5a6)

    payload = {
        'embeds': [{
            'title': title[:256],
            'description': desc,
            'color': color,
            'url': run_url or None,
            'footer': {'text': f"id={e.get('id','')}  type={e.get('type','')}"},
        }]
    }
    # Discord rejects the default 'Python-urllib/X.Y' User-Agent with 403,
    # so identify ourselves.
    req = urllib.request.Request(
        webhook,
        data=json.dumps(payload).encode('utf-8'),
        headers={
            'Content-Type': 'application/json',
            'User-Agent': 'ai-hiring-bot/1.0 (+https://github.com/yukunchen/aihiringsystem)',
        },
        method='POST',
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return 200 <= resp.status < 300
    except urllib.error.HTTPError as err:
        body = err.read().decode('utf-8', errors='replace')[:200]
        print(f"  HTTPError {err.code}: {body}", file=sys.stderr)
        return False
    except Exception as err:
        print(f"  post failed: {err}", file=sys.stderr)
        return False

dirty = False
for e in entries:
    if not should_push(e):
        continue
    eid = e.get('id', '?')
    print(f"  pushing {eid} ({e.get('type','?')})")
    if post(e):
        e['pushed_discord'] = True
        dirty = True
        print(f"  pushed {eid}")
    else:
        print(f"  skip marking {eid}; will retry next scan")

if dirty:
    tmp = path + '.tmp'
    with open(tmp, 'w') as f:
        for e in entries:
            if e is None:
                f.write('\n')
            elif isinstance(e, dict) and '__raw__' in e:
                f.write(e['__raw__'] + '\n')
            else:
                f.write(json.dumps(e) + '\n')
    os.replace(tmp, path)
PYEOF
}

# Install a termination handler for systemd stop.
trap 'log "signal received, exiting cleanly"; exit 0' TERM INT

while :; do
    scan_and_push || log "scan error (continuing)"
    sleep "$POLL_INTERVAL"
done
