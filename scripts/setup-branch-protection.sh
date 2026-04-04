#!/bin/bash
# Setup branch protection for master branch
# Prerequisites: gh CLI installed and authenticated
# Usage: ./scripts/setup-branch-protection.sh <owner/repo>

set -euo pipefail

REPO="${1:-}"
if [ -z "$REPO" ]; then
    echo "Usage: $0 <owner/repo>"
    echo "Example: $0 yukunchen/aihiringsystem"
    exit 1
fi

echo "Setting up branch protection for $REPO master branch..."

gh api "repos/$REPO/branches/master/protection" \
  --method PUT \
  --input - << 'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["backend", "ai-service", "frontend"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1
  },
  "restrictions": null
}
EOF

echo "Branch protection set successfully."
echo "  - Required CI checks: backend, ai-service, frontend"
echo "  - Required reviews: 1 (self-review allowed)"
