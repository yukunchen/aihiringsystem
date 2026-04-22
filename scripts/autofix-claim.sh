#!/usr/bin/env bash
# Advisory lock for autofix: check whether issue #N is still actionable,
# then claim it by adding the `autofix-in-progress` label. Prevents two
# concurrent Claude sessions from fixing the same issue (which happened
# with issue #125 -> PRs #126 and #127 both landing the same fix).
#
# Usage:
#   autofix-claim.sh <issue-number> [repo]
#
# Exit codes:
#   0  — claim acquired, caller should proceed with the fix
#   1  — already claimed / closed / has open PR / not found; caller should skip
#   2  — usage error or gh CLI failure
set -euo pipefail

ISSUE="${1:-}"
REPO="${2:-$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo '')}"
LOCK_LABEL="autofix-in-progress"

if [[ -z "$ISSUE" || -z "$REPO" ]]; then
    echo "usage: $0 <issue-number> [owner/repo]" >&2
    exit 2
fi

echo "Checking issue #${ISSUE} in ${REPO}..."

ISSUE_JSON=$(gh issue view "$ISSUE" --repo "$REPO" --json state,labels 2>/dev/null || echo '')
if [[ -z "$ISSUE_JSON" ]]; then
    echo "  issue not found or gh error — skip"
    exit 1
fi

STATE=$(echo "$ISSUE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('state',''))")
LABELS=$(echo "$ISSUE_JSON" | python3 -c "import sys,json; print(','.join(l['name'] for l in json.load(sys.stdin).get('labels',[])))")

if [[ "$STATE" != "OPEN" ]]; then
    echo "  state=${STATE} — skip"
    exit 1
fi

if [[ ",${LABELS}," == *",${LOCK_LABEL},"* ]]; then
    echo "  already has ${LOCK_LABEL} label — another session is on it, skip"
    exit 1
fi

# Any open PR that closes this issue? Use GitHub's native linkage.
OPEN_PRS=$(gh pr list --repo "$REPO" --state open --json number,closingIssuesReferences \
    --jq "[.[] | select(.closingIssuesReferences[]?.number == ${ISSUE}) | .number] | join(\",\")" 2>/dev/null || echo '')
if [[ -n "$OPEN_PRS" ]]; then
    echo "  open PR(s) already close this issue: #${OPEN_PRS} — skip"
    exit 1
fi

# Ensure the lock label exists, then add it. The add itself is the claim.
gh label create "$LOCK_LABEL" --repo "$REPO" --color fbca04 \
    --description "An autofix agent is currently working on this issue" 2>/dev/null || true

if gh issue edit "$ISSUE" --repo "$REPO" --add-label "$LOCK_LABEL" >/dev/null 2>&1; then
    echo "  claimed issue #${ISSUE}"
    exit 0
fi

echo "  failed to add lock label — skip"
exit 1
