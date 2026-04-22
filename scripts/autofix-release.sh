#!/usr/bin/env bash
# Release the autofix advisory lock on issue #N by removing the
# `autofix-in-progress` label. Safe to run even if the label is absent.
#
# Usage:
#   autofix-release.sh <issue-number> [repo]
set -euo pipefail

ISSUE="${1:-}"
REPO="${2:-$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo '')}"
LOCK_LABEL="autofix-in-progress"

if [[ -z "$ISSUE" || -z "$REPO" ]]; then
    echo "usage: $0 <issue-number> [owner/repo]" >&2
    exit 2
fi

gh issue edit "$ISSUE" --repo "$REPO" --remove-label "$LOCK_LABEL" >/dev/null 2>&1 || true
echo "released autofix lock on #${ISSUE}"
