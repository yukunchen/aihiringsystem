#!/bin/bash
# Canonical source for /opt/ai-hiring/scripts/check-notifications.sh on the VPS.
# Used by Claude Code Stop and UserPromptSubmit hooks to inject queued
# notifications as additional context.
#
# Role filtering:
#   CLAUDE_ROLE=fixer         → consumes only `type: new_issue` from queue.jsonl.
#                               Ignores orchestrator-queue.jsonl entirely. This
#                               is what the long-running background session
#                               that runs autofix-claim.sh + opens PRs should
#                               use so it does not gobble review pings meant
#                               for the human.
#   CLAUDE_ROLE=orchestrator  → consumes `pr_ready` / deploy notifications
#                               from orchestrator-queue.jsonl first, falls
#                               back to queue.jsonl. This is what interactive
#                               user-facing sessions should use so the human
#                               hears about PRs ready for review, prod
#                               health, etc.
#   unset / other             → legacy behavior: reads queue.jsonl, consumes
#                               any unread entry regardless of type.
#
# Exit codes follow the hook semantics per event (set from stdin):
#   Stop event         → exit 2 wakes Claude and sends reason as context.
#   UserPromptSubmit   → exit 0 with additionalContext (exit 2 would block
#                        the user's prompt).

set -u

QUEUE_ISSUES="/opt/ai-hiring/notifications/queue.jsonl"
QUEUE_ORCHESTRATOR="/opt/ai-hiring/notifications/orchestrator-queue.jsonl"
LOCK="/tmp/ai-hiring-notify.lock"
ROLE="${CLAUDE_ROLE:-}"

# Capture hook input (Claude Code passes JSON on stdin).
HOOK_INPUT=""
if [ ! -t 0 ]; then
    HOOK_INPUT=$(cat)
fi
HOOK_EVENT=$(printf '%s' "$HOOK_INPUT" | python3 -c "
import json, sys
try:
    print(json.load(sys.stdin).get('hook_event_name',''))
except Exception:
    print('')
" 2>/dev/null)
[ -z "$HOOK_EVENT" ] && HOOK_EVENT="Stop"

# Serialize access across concurrent hook invocations.
exec 9>"$LOCK"
flock -n 9 || exit 0

# Pick which queue(s) to read and which entry types are eligible,
# based on CLAUDE_ROLE. Order matters: orchestrator prefers PR pings
# over generic deploy notifications.
case "$ROLE" in
    fixer)
        QUEUES_ORDER="$QUEUE_ISSUES"
        ALLOWED_TYPES="new_issue"
        ;;
    orchestrator)
        QUEUES_ORDER="$QUEUE_ORCHESTRATOR $QUEUE_ISSUES"
        # Orchestrator handles PR pings + generic deploy notifications.
        # new_issue belongs to the fixer role — leave those untouched so
        # the fixer session can consume them.
        ALLOWED_TYPES="pr_ready,deploy,"
        ;;
    *)
        QUEUES_ORDER="$QUEUE_ISSUES"
        ALLOWED_TYPES="*"
        ;;
esac

# Find the first unread entry across the chosen queues that matches
# the role's allowed types, and atomically mark it read.
FOUND=""
for Q in $QUEUES_ORDER; do
    [ -f "$Q" ] || continue
    ENTRY=$(ALLOWED="$ALLOWED_TYPES" ROLE="$ROLE" python3 - "$Q" << 'PYEOF'
import json, os, sys
path = sys.argv[1]
allowed = os.environ.get('ALLOWED', '*').split(',')
role = os.environ.get('ROLE', '')
allow_all = '*' in allowed

with open(path) as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    line = line.rstrip('\n')
    if not line:
        continue
    try:
        obj = json.loads(line)
    except Exception:
        continue
    if obj.get('read'):
        continue
    # Respect explicit 'target' when the role is known.
    target = obj.get('target')
    if role and target and target != role:
        continue
    t = obj.get('type', '')
    if not allow_all and t not in allowed:
        continue
    print(i)
    print(json.dumps(obj))
    break
PYEOF
)
    if [ -n "$ENTRY" ]; then
        LINE_NUM=$(echo "$ENTRY" | head -1)
        PAYLOAD=$(echo "$ENTRY" | tail -1)
        python3 - "$Q" "$LINE_NUM" << 'PYEOF'
import json, sys
path, lineno = sys.argv[1], int(sys.argv[2])
with open(path) as f:
    lines = f.readlines()
lines[lineno] = json.dumps({**json.loads(lines[lineno]), 'read': True}) + '\n'
with open(path, 'w') as f:
    f.writelines(lines)
PYEOF
        FOUND="$PAYLOAD"
        break
    fi
done

if [ -z "$FOUND" ]; then
    exit 0
fi

MESSAGE=$(echo "$FOUND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)
DETAILS=$(echo "$FOUND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('details',''))" 2>/dev/null)
NTYPE=$(echo "$FOUND" | python3 -c "import sys,json; print(json.load(sys.stdin).get('type','deploy'))" 2>/dev/null)

case "$NTYPE" in
    new_issue)
        CONTEXT="New autofix issue detected:

$MESSAGE

$DETAILS

Before you do anything else: run \`scripts/autofix-claim.sh <issue-number>\` to acquire the cross-session lock. If it exits non-zero, another session is on it — stop. Otherwise proceed: read the issue, analyze the root cause, write a fix, run tests, and open a PR (body must include \`Fixes #<N>\`). Do NOT merge."
        ;;
    pr_ready)
        CONTEXT="A PR opened by the autofix pipeline is waiting for review:

$MESSAGE

$DETAILS

Surface this to the user so they can review and merge. Do not merge on their behalf."
        ;;
    *)
        CONTEXT="Notification from deployment pipeline:

$MESSAGE

$DETAILS

You can take action if appropriate (investigate failures, open issues)."
        ;;
esac

HOOK_EVENT="$HOOK_EVENT" CONTEXT="$CONTEXT" python3 << 'PYEOF'
import json, os
context = os.environ.get('CONTEXT', '')
event = os.environ.get('HOOK_EVENT', 'Stop')
if event == 'Stop':
    print(json.dumps({'decision': 'block', 'reason': context}))
else:
    print(json.dumps({
        'hookSpecificOutput': {
            'hookEventName': event,
            'additionalContext': context
        }
    }))
PYEOF

# Only Stop should use exit 2. Others return 0 so user prompts / tool
# calls are not rejected.
if [ "$HOOK_EVENT" = "Stop" ]; then
    exit 2
fi
exit 0
