#!/bin/bash
# scripts/autofix-issue.sh <issue-number>
#
# Canonical source for /opt/ai-hiring/scripts/autofix-issue.sh on the VPS.
# Invoked by watch-issues.sh (cron, every 2 min) when a new `autofix`-labeled
# issue appears. Creates a git worktree, runs Claude Code headlessly to
# produce a fix, pushes the branch, opens a PR, and kicks off the reviewer.
#
# Design notes / failure-handling rules:
#
# * Strict mode is enabled, but `claude -p`'s exit status is captured
#   explicitly with `|| CLAUDE_EXIT=$?` so `set -e` never silently kills
#   the script mid-pipeline. A previous version used
#   `claude -p ... | tee -a LOG` + `CLAUDE_EXIT=${PIPESTATUS[0]}` — when
#   pipefail triggered, the CLAUDE_EXIT capture never ran and neither did
#   the cleanup/notify paths. (Observed on issue #140: max-turns hit, no
#   worktree cleanup, no lock release, no Discord ping.)
#
# * On ANY failure path (claude errored, zero commits, push failed, pr
#   create failed), the script:
#     1. removes the worktree + local branch (so the next retry starts
#        clean);
#     2. calls scripts/autofix-release.sh to drop the
#        `autofix-in-progress` label so the issue is re-eligible;
#     3. writes a `type=autofix_fail` entry to the orchestrator queue so
#        the Discord watcher surfaces it to the human.
#
# * Logging: writes directly to LOG_FILE via `>>` — not `tee -a` — because
#   the caller (watch-issues.sh) already redirects stdout/stderr into the
#   same log. The old tee approach doubled every line.
#
# * Advisory file lock per issue number serializes concurrent dispatches
#   (watch-issues cron + future manual triggers). This is belt-and-braces
#   on top of the GitHub `autofix-in-progress` label lock.
#
# Env knobs:
#   MAX_TURNS        override --max-turns for claude -p (default 80).
#                    40 was too tight for issues needing cross-module
#                    understanding (e.g. #140 dedup spanning FE+BE).

set -euo pipefail

ISSUE_NUMBER=${1:?Usage: $0 <issue-number>}
REPO="yukunchen/aihiringsystem"
REPO_DIR="/home/ubuntu/WS/ai-hiring-fresh/aihiringsystem-master"
WORKTREES_DIR="$REPO_DIR/worktrees"
LOG_FILE="/opt/ai-hiring/logs/autofix-${ISSUE_NUMBER}.log"
ORCHESTRATOR_QUEUE="/opt/ai-hiring/notifications/orchestrator-queue.jsonl"
DISPATCH_LOCK="/tmp/ai-hiring-autofix-${ISSUE_NUMBER}.lock"
MAX_TURNS="${MAX_TURNS:-80}"

# Populated during setup so cleanup paths can reference them.
BRANCH=""
WORKTREE_PATH=""
ISSUE_TITLE=""
ISSUE_URL=""

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [issue-#${ISSUE_NUMBER}] $*" >> "$LOG_FILE"
}

# Push a failure notification to the orchestrator queue. The Discord
# watcher picks up `target=orchestrator` entries; the orchestrator Claude
# session's UserPromptSubmit hook consumes `type=autofix_fail`.
notify_autofix_failed() {
    local reason="$1"
    MSG="❌ Autofix FAILED for issue #${ISSUE_NUMBER}: ${reason}" \
    DETAILS="Title: ${ISSUE_TITLE:-?}
URL: ${ISSUE_URL:-?}
Reason: ${reason}
Log: ${LOG_FILE}

Advisory lock has been released — issue is re-eligible next time \`autofix\` label is (re-)applied." \
    ISSUE_URL_VAR="${ISSUE_URL:-}" \
    ISSUE_NUMBER_VAR="${ISSUE_NUMBER}" \
    python3 - <<'PYEOF' || true
import json, datetime, os
entry = {
  'id': f'autofix-fail-{os.environ["ISSUE_NUMBER_VAR"]}-{datetime.datetime.utcnow().strftime("%H%M%S")}',
  'timestamp': datetime.datetime.utcnow().isoformat() + 'Z',
  'read': False,
  'type': 'autofix_fail',
  'target': 'orchestrator',
  'message': os.environ['MSG'],
  'details': os.environ['DETAILS'],
  'run_url': os.environ.get('ISSUE_URL_VAR', '')
}
queue = '/opt/ai-hiring/notifications/orchestrator-queue.jsonl'
os.makedirs(os.path.dirname(queue), exist_ok=True)
with open(queue, 'a') as f:
    f.write(json.dumps(entry) + '\n')
PYEOF
}

# Shared cleanup on any failure path.
cleanup_failed() {
    local reason="$1"
    log "FAILURE: $reason — cleaning up"
    if [ -n "$WORKTREE_PATH" ] && [ -d "$WORKTREE_PATH" ]; then
        git -C "$REPO_DIR" worktree remove --force "$WORKTREE_PATH" 2>/dev/null || rm -rf "$WORKTREE_PATH"
    fi
    if [ -n "$BRANCH" ]; then
        git -C "$REPO_DIR" branch -D "$BRANCH" 2>/dev/null || true
    fi
    # Release the GitHub advisory lock so the issue can be retried.
    if [ -x "$REPO_DIR/scripts/autofix-release.sh" ]; then
        bash "$REPO_DIR/scripts/autofix-release.sh" "$ISSUE_NUMBER" >> "$LOG_FILE" 2>&1 || true
    fi
    notify_autofix_failed "$reason"
}

# Fail-fast helper: log, cleanup, exit non-zero.
fail() {
    cleanup_failed "$1"
    exit 1
}

# Serialize concurrent dispatches for the same issue. Non-blocking — if
# another autofix is already running for this issue, bail out quietly.
exec 200>"$DISPATCH_LOCK"
if ! flock -n 200; then
    log "Another autofix is already running for #${ISSUE_NUMBER} — skipping"
    exit 0
fi

log "Starting autofix for issue #$ISSUE_NUMBER (max_turns=$MAX_TURNS)"

# ── 1. Fetch issue details ──────────────────────────────────────────────────
ISSUE_JSON=$(gh issue view "$ISSUE_NUMBER" \
    --repo "$REPO" \
    --json number,title,body,url) || fail "gh issue view failed"

ISSUE_TITLE=$(echo "$ISSUE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['title'])")
ISSUE_BODY=$(echo "$ISSUE_JSON"  | python3 -c "import sys,json; print(json.load(sys.stdin)['body'] or '(no description provided)')")
ISSUE_URL=$(echo "$ISSUE_JSON"   | python3 -c "import sys,json; print(json.load(sys.stdin)['url'])")

log "Issue: $ISSUE_TITLE"

# ── 2. Derive branch name ───────────────────────────────────────────────────
SLUG=$(echo "$ISSUE_TITLE" \
    | tr '[:upper:]' '[:lower:]' \
    | tr -cs 'a-z0-9' '-' \
    | sed 's/^-//;s/-$//' \
    | cut -c1-40)
[ -z "$SLUG" ] && SLUG="fix"
BRANCH="fix/issue-${ISSUE_NUMBER}-${SLUG}"

log "Branch: $BRANCH"

# ── 3. Create git worktree ──────────────────────────────────────────────────
WORKTREE_PATH="$WORKTREES_DIR/issue-${ISSUE_NUMBER}"

cd "$REPO_DIR"
git fetch origin master --quiet || fail "git fetch failed"

if [ -d "$WORKTREE_PATH" ]; then
    log "Removing stale worktree at $WORKTREE_PATH"
    git worktree remove --force "$WORKTREE_PATH" 2>/dev/null || rm -rf "$WORKTREE_PATH"
    git branch -D "$BRANCH" 2>/dev/null || true
fi

git worktree add -b "$BRANCH" "$WORKTREE_PATH" origin/master || fail "git worktree add failed"
log "Worktree created at $WORKTREE_PATH"

# ── 4. Build the Claude prompt ──────────────────────────────────────────────
CLAUDE_PROMPT="You are an expert software engineer working on an AI-powered hiring platform (aihiringsystem).

## Your Task
Fix GitHub issue #${ISSUE_NUMBER} in the repository ${REPO}.

## Issue Details
Title: ${ISSUE_TITLE}
URL: ${ISSUE_URL}

Description:
${ISSUE_BODY}

## Instructions
1. Read CLAUDE.md first to understand the architecture and conventions
2. Explore the relevant source files to fully understand the problem
3. Implement the minimal correct fix — do NOT refactor unrelated code
4. Write or update tests to cover your fix (TDD: failing test first, then fix)
5. Run the relevant test suite to confirm it passes:
   - Backend: cd ai-hiring-backend && ./gradlew test
   - AI service: cd ai-matching-service && python -m pytest tests/
   - Frontend: cd frontend && npm test -- --run
6. Stage and commit with message format: 'fix: <short description>'
   Include 'Closes #${ISSUE_NUMBER}' in the commit body.
   Example:
     git commit -m \$'fix: correct resume upload validation\\n\\nCloses #${ISSUE_NUMBER}'
7. Do NOT push or create a PR — the calling script handles that.

You are working in the branch '${BRANCH}'. All file edits apply to this worktree.

ROLE RESTRICTION — FIXER ONLY:
- Your status in any comment or commit message must be 'READY FOR REVIEW', never 'FIXED' or 'VERIFIED'.
- Do NOT claim staging verification. A separate Reviewer and Verifier agent will handle that independently.
- The commit body MUST contain 'Closes #${ISSUE_NUMBER}' on its own line."

# ── 5. Run Claude Code (no tee — caller already redirects stdout to LOG) ────
log "Running claude -p in worktree..."
cd "$WORKTREE_PATH"

export PATH="/usr/local/bin:/usr/bin:/bin:/home/ubuntu/.npm-global/bin:/home/ubuntu/.local/bin:$PATH"
export CLAUDE_ROLE=fixer

# Capture exit explicitly with `|| CLAUDE_EXIT=$?` so strict-mode never
# skips the failure-handling block below.
CLAUDE_EXIT=0
claude -p "$CLAUDE_PROMPT" \
    --allowedTools "Bash,Read,Edit,Write,Glob,Grep" \
    --max-turns "$MAX_TURNS" \
    >> "$LOG_FILE" 2>&1 || CLAUDE_EXIT=$?

log "claude exited with code $CLAUDE_EXIT"

if [ "$CLAUDE_EXIT" -ne 0 ]; then
    fail "claude -p exited with code $CLAUDE_EXIT (often 'max-turns' exhaustion — try MAX_TURNS=120)"
fi

# ── 6. Check for new commits ────────────────────────────────────────────────
COMMIT_COUNT=$(git log origin/master..HEAD --oneline | wc -l | tr -d ' ')
if [ "$COMMIT_COUNT" -eq 0 ]; then
    # Not infra-broken; Claude ran clean but couldn't decide what to change.
    # Comment on the issue, release lock, push a soft Discord notification.
    log "Claude made no commits — leaving comment on issue"
    git worktree remove --force "$WORKTREE_PATH" 2>/dev/null || true
    git -C "$REPO_DIR" branch -D "$BRANCH" 2>/dev/null || true
    bash "$REPO_DIR/scripts/autofix-release.sh" "$ISSUE_NUMBER" >> "$LOG_FILE" 2>&1 || true
    gh issue comment "$ISSUE_NUMBER" \
        --repo "$REPO" \
        --body "I analyzed this issue but could not determine a fix to commit. Please provide more details or clarify the expected behavior." \
        >> "$LOG_FILE" 2>&1 || true
    notify_autofix_failed "No commits produced — issue may be unclear"
    exit 0
fi

log "Claude made $COMMIT_COUNT commit(s)"

# ── 7. Push branch ──────────────────────────────────────────────────────────
log "Pushing branch $BRANCH..."
git push origin "$BRANCH" >> "$LOG_FILE" 2>&1 || fail "git push failed"

# ── 8. Open PR ──────────────────────────────────────────────────────────────
log "Creating PR..."
COMMIT_SUMMARY=$(git log origin/master..HEAD --pretty=format:"- %s")

PR_URL=$(gh pr create \
    --repo "$REPO" \
    --base master \
    --head "$BRANCH" \
    --title "fix: ${ISSUE_TITLE}" \
    --body "**Status**: READY FOR REVIEW

Fixes #${ISSUE_NUMBER}: ${ISSUE_TITLE}

**Issue**: ${ISSUE_URL}

## Changes

${COMMIT_SUMMARY}

## Note

An independent Reviewer agent will post a structured review comment to this PR.
After merge, a separate Verifier agent will probe the live staging environment.
Neither agent can modify source files.

---
*Auto-generated by Claude Code Fixer agent in response to the \`autofix\` label.*

Closes #${ISSUE_NUMBER}
issue: #${ISSUE_NUMBER}") || fail "gh pr create failed"

log "PR created: $PR_URL"

# ── 9. Comment on the issue ─────────────────────────────────────────────────
gh issue comment "$ISSUE_NUMBER" \
    --repo "$REPO" \
    --body "Fixer agent has opened a fix: $PR_URL

Branch: \`${BRANCH}\`
Status: **READY FOR REVIEW**

An independent Reviewer agent will post a structured code review comment to the PR shortly. After you merge, a Verifier agent will automatically probe the live staging environment and post evidence to this issue." \
    >> "$LOG_FILE" 2>&1 || log "WARNING: gh issue comment failed"

log "Commented on issue #$ISSUE_NUMBER"

# ── 10. Launch Reviewer agent (background, non-blocking) ────────────────────
PR_NUMBER=$(basename "$PR_URL")
log "Launching reviewer for PR #$PR_NUMBER (issue #$ISSUE_NUMBER)..."
nohup /opt/ai-hiring/scripts/review-pr.sh \
    "$ISSUE_NUMBER" "$PR_URL" "$PR_NUMBER" \
    >> "/opt/ai-hiring/logs/review-pr-${ISSUE_NUMBER}.log" 2>&1 &
log "Reviewer launched (PID $!) — see review-pr-${ISSUE_NUMBER}.log"

log "Autofix complete for issue #$ISSUE_NUMBER"
