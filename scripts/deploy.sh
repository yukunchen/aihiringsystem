#!/bin/bash
set -e

ENV=$1
VERSION=$2

if [ -z "$ENV" ]; then
    echo "Usage: $0 <dev|staging|prod> [version]"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="/opt/ai-hiring/docker/$ENV"

echo "🚀 Deploying $ENV environment..."

cd "$COMPOSE_DIR"

# Update image tag if version specified
if [ -n "$VERSION" ]; then
    if [ -f .env ]; then
        sed -i "s/IMAGE_TAG=.*/IMAGE_TAG=$VERSION/" .env
        echo "📦 Using image tag: $VERSION"
    else
        echo "❌ .env file not found at $COMPOSE_DIR/.env"
        exit 1
    fi
fi

# Pull latest images
echo "📥 Pulling latest images..."
docker compose pull

# Rolling update
echo "🔄 Starting containers..."
docker compose up -d --no-deps

# Wait for startup
echo "⏳ Waiting for services to start..."
sleep 30

# Health check
"$SCRIPT_DIR/health-check.sh" "$ENV"

echo "✅ $ENV deployment complete!"
