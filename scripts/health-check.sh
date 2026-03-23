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

# Check backend (any response means it's running)
echo "  Checking backend on port $BACKEND_PORT..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$BACKEND_PORT/api/auth/login" -X POST -H "Content-Type: application/json" -d '{}' 2>&1)

if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 500 ]; then
    echo "  ✅ Backend healthy (HTTP $HTTP_CODE)"
else
    echo "  ❌ Backend health check failed (HTTP $HTTP_CODE)"
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
