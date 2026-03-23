#!/bin/bash
set -e

ENV=$1
VERSION=$2

if [ -z "$ENV" ] || [ -z "$VERSION" ]; then
    echo "Usage: $0 <dev|staging|prod> <version>"
    echo "Example: $0 prod abc123def456"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔄 Rolling back $ENV to version $VERSION..."
"$SCRIPT_DIR/deploy.sh" "$ENV" "$VERSION"
echo "✅ Rollback complete!"
