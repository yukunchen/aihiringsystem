#!/bin/bash
set -e

ENV=$1
VERSION=$2
GHCR_TOKEN=$3

if [ -z "$ENV" ]; then
    echo "Usage: $0 <dev|staging|prod> [version] [ghcr_token]"
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

# Login to GHCR if token provided
if [ -n "$GHCR_TOKEN" ]; then
    echo "🔑 Logging in to GitHub Container Registry..."
    echo "$GHCR_TOKEN" | docker login ghcr.io -u yukunchen --password-stdin
fi

# Pull latest images
echo "📥 Pulling latest images..."
docker compose pull

# Rolling update
echo "🔄 Starting containers..."
docker compose up -d --no-deps

# Wait for backend to be ready (polls up to 5 minutes)
echo "⏳ Waiting for backend to be ready..."
BACKEND_PORT=$(grep "BACKEND_PORT=" "$COMPOSE_DIR/.env" 2>/dev/null | cut -d= -f2 || echo "8080")
TIMEOUT=300
ELAPSED=0
until curl -sf -o /dev/null -w "%{http_code}" "http://localhost:${BACKEND_PORT}/api/auth/login" \
    -X POST -H "Content-Type: application/json" -d '{}' 2>/dev/null | grep -qE "^[234]"; do
    if [ $ELAPSED -ge $TIMEOUT ]; then
        echo "❌ Backend did not become ready within ${TIMEOUT}s"
        exit 1
    fi
    sleep 10
    ELAPSED=$((ELAPSED + 10))
    echo "  ... still waiting (${ELAPSED}s)"
done
echo "✅ Backend ready after ${ELAPSED}s"

# Health check
"$SCRIPT_DIR/health-check.sh" "$ENV"

echo "✅ $ENV deployment complete!"
