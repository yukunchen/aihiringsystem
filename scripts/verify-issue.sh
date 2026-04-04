#!/bin/bash
set -euo pipefail

ISSUE_NUMBER="${1:-}"
BASE_URL="${2:-}"
ISSUE_BODY="${3:-}"

if [ -z "$ISSUE_NUMBER" ] || [ -z "$BASE_URL" ]; then
    echo "Usage: $0 <issue_number> <base_url> [issue_body]"
    echo "Example: $0 21 http://localhost:8082"
    exit 1
fi

LOG_PREFIX="[verify-issue #$ISSUE_NUMBER]"
echo "$LOG_PREFIX Starting verification against $BASE_URL"
echo "$LOG_PREFIX Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

PASS=true
RESULTS=()

run_test() {
    local name="$1"
    local result="$2"
    if [ "$result" = "true" ]; then
        RESULTS+=("PASS: $name")
        echo "$LOG_PREFIX PASS: $name"
    else
        RESULTS+=("FAIL: $name")
        echo "$LOG_PREFIX FAIL: $name"
        PASS=false
    fi
}

# 1. Auth
LOGIN_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' 2>/dev/null || echo '{}')
TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")

if [ -n "$TOKEN" ]; then
    run_test "Auth login" "true"
else
    run_test "Auth login" "false"
    echo "$LOG_PREFIX Cannot continue without auth token"
    echo "$LOG_PREFIX FINAL RESULT: FAIL"
    exit 1
fi

# 2. Jobs list
JOBS_RESP=$(curl -sf "$BASE_URL/api/jobs" -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo '{}')
JCODE=$(echo "$JOBS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")
[ "$JCODE" = "200" ] && run_test "Jobs list API" "true" || run_test "Jobs list API" "false"

# 3. Resume upload
echo "verify-issue-test" > /tmp/verify-issue-$ISSUE_NUMBER.txt
UPLOAD_RESP=$(curl -sf -X POST "$BASE_URL/api/resumes/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@/tmp/verify-issue-$ISSUE_NUMBER.txt;type=text/plain" 2>/dev/null || echo '{}')
UCODE=$(echo "$UPLOAD_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")
[ "$UCODE" = "200" ] && run_test "Resume upload API" "true" || run_test "Resume upload API" "false"
rm -f /tmp/verify-issue-$ISSUE_NUMBER.txt

# 4. AI service health
AI_HEALTH=$(curl -sf "http://localhost:8002/health" 2>/dev/null || echo "")
[ -n "$AI_HEALTH" ] && run_test "AI service health" "true" || run_test "AI service health" "false"

# Summary
echo "$LOG_PREFIX ===================================="
echo "$LOG_PREFIX Verification Summary for Issue #$ISSUE_NUMBER"
for r in "${RESULTS[@]}"; do
    echo "$LOG_PREFIX   $r"
done
echo "$LOG_PREFIX ===================================="

if [ "$PASS" = "true" ]; then
    echo "$LOG_PREFIX FINAL RESULT: PASS"
    exit 0
else
    echo "$LOG_PREFIX FINAL RESULT: FAIL"
    exit 1
fi
