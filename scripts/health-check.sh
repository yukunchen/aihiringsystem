#!/bin/bash
set -e

ENV=$1

case $ENV in
    dev)
        BACKEND_PORT=8081
        AI_PORT=8001
        ;;
    staging)
        BACKEND_PORT=8082
        AI_PORT=8002
        ;;
    prod)
        BACKEND_PORT=8080
        AI_PORT=8000
        ;;
    *)
        echo "❌ Unknown environment: $ENV"
        echo "Usage: $0 <dev|staging|prod>"
        exit 1
        ;;
esac

echo "🔍 Health check for $ENV environment..."

# Check backend (using login endpoint since it's always public)
echo "  Checking backend on port $BACKEND_PORT..."
if curl -sf "http://localhost:$BACKEND_PORT/api/auth/login" -X POST -H "Content-Type: application/json" -d '{}' > /dev/null 2>&1; then
    echo "  ✅ Backend healthy (login endpoint reachable)"
else
    echo "  ❌ Backend health check failed"
    exit 1
fi

# Check AI service
echo "  Checking AI service on port $AI_PORT..."
if curl -sf "http://localhost:$AI_PORT/health" > /dev/null 2>&1; then
    echo "  ✅ AI service healthy"
else
    echo "  ❌ AI service health check failed"
    exit 1
fi

echo "✅ All services healthy for $ENV environment"
