#!/bin/bash
set -euo pipefail

# Real-data E2E verification script for staging
# Usage: ./verify-staging.sh <base_url> [data_dir]
# Example: ./verify-staging.sh http://184.32.94.23:8082 ./trainingdata

BASE_URL="${1:-}"
DATA_DIR="${2:-$(dirname "$0")/../trainingdata}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$BASE_URL" ]; then
    echo "Usage: $0 <base_url> [data_dir]"
    echo "Example: $0 http://localhost:8082 ./trainingdata"
    exit 1
fi

LOG_PREFIX="[verify-staging]"
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

echo "$LOG_PREFIX Starting real-data verification against $BASE_URL"
echo "$LOG_PREFIX Data directory: $DATA_DIR"

# ============================================
# 1. Auth - Login and get token
# ============================================
echo "$LOG_PREFIX --- Step 1: Authentication ---"
LOGIN_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' 2>/dev/null || echo '{}')
TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")

if [ -n "$TOKEN" ]; then
    run_test "Auth login" "true"
else
    run_test "Auth login" "false"
    echo "$LOG_PREFIX Cannot continue without auth token"
    exit 1
fi

# ============================================
# 2. Get department ID
# ============================================
echo "$LOG_PREFIX --- Step 2: Get departments ---"
DEPT_RESPONSE=$(curl -sf "$BASE_URL/api/departments" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo '[]')
DEPT_ID=$(echo "$DEPT_RESPONSE" | python3 -c "
import sys,json
d = json.load(sys.stdin)
deps = d.get('data',[]) if isinstance(d.get('data'), list) else d.get('data',{}).get('content',[])
if deps and len(deps) > 0:
    print(deps[0].get('id',''))
else:
    print('')
" 2>/dev/null || echo "")

if [ -n "$DEPT_ID" ]; then
    echo "$LOG_PREFIX Found department: $DEPT_ID"
    run_test "Get departments" "true"
else
    echo "$LOG_PREFIX No departments found, will try without departmentId"
    run_test "Get departments" "false"
fi

# ============================================
# 3. Create JD from real data
# ============================================
echo "$LOG_PREFIX --- Step 3: Create JD ---"
JD_FILE="$SCRIPT_DIR/test-data/jd-unity.json"
if [ ! -f "$JD_FILE" ]; then
    echo "$LOG_PREFIX JD test data file not found: $JD_FILE"
    run_test "Create JD" "false"
else
    # Inject department ID
    JD_BODY=$(python3 -c "
import json
with open('$JD_FILE') as f:
    d = json.load(f)
d['departmentId'] = '$DEPT_ID' if '$DEPT_ID' else None
# Remove None values
d = {k:v for k,v in d.items() if v is not None}
print(json.dumps(d, ensure_ascii=False))
" 2>/dev/null)

    CREATE_JD_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/jobs" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "$JD_BODY" 2>/dev/null || echo '{}')

    JD_ID=$(echo "$CREATE_JD_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
    JD_CODE=$(echo "$CREATE_JD_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")

    if [ "$JD_CODE" = "200" ] && [ -n "$JD_ID" ]; then
        echo "$LOG_PREFIX Created JD: $JD_ID"
        run_test "Create JD (Unity 中高级开发工程师)" "true"
    else
        echo "$LOG_PREFIX Failed to create JD. Response: $CREATE_JD_RESPONSE"
        run_test "Create JD" "false"
    fi
fi

# ============================================
# 4. Upload real resumes
# ============================================
echo "$LOG_PREFIX --- Step 4: Upload resumes ---"
RESUME_IDS=()

for resume_file in "$DATA_DIR"/*.pdf; do
    [ -f "$resume_file" ] || continue
    filename=$(basename "$resume_file")
    echo "$LOG_PREFIX Uploading: $filename"

    UPLOAD_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/resumes/upload" \
        -H "Authorization: Bearer $TOKEN" \
        -F "file=@$resume_file;type=application/pdf" \
        -F "source=MANUAL" \
        2>/dev/null || echo '{}')

    RESUME_ID=$(echo "$UPLOAD_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
    UPLOAD_CODE=$(echo "$UPLOAD_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")
    RESUME_STATUS=$(echo "$UPLOAD_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status',''))" 2>/dev/null || echo "")

    if [ "$UPLOAD_CODE" = "200" ] && [ -n "$RESUME_ID" ]; then
        echo "$LOG_PREFIX Uploaded: $filename → $RESUME_ID (status: $RESUME_STATUS)"
        RESUME_IDS+=("$RESUME_ID")
        run_test "Upload resume: $filename" "true"
    else
        echo "$LOG_PREFIX Failed to upload: $filename. Response: $UPLOAD_RESPONSE"
        run_test "Upload resume: $filename" "false"
    fi
done

if [ ${#RESUME_IDS[@]} -eq 0 ]; then
    echo "$LOG_PREFIX No resumes uploaded, cannot continue"
    run_test "Resume upload count" "false"
fi

# ============================================
# 5. Wait for vectorization
# ============================================
echo "$LOG_PREFIX --- Step 5: Wait for vectorization ---"
VECT_TIMEOUT=120
VECT_INTERVAL=5
VECT_ELAPSED=0
ALL_VECTORIZED=false

while [ $VECT_ELAPSED -lt $VECT_TIMEOUT ]; do
    ALL_VECTORIZED=true
    for rid in "${RESUME_IDS[@]}"; do
        STATUS_RESPONSE=$(curl -sf "$BASE_URL/api/resumes/$rid" \
            -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo '{}')
        STATUS=$(echo "$STATUS_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status',''))" 2>/dev/null || echo "")
        echo "$LOG_PREFIX Resume $rid status: $STATUS"
        if [ "$STATUS" != "AI_PROCESSED" ]; then
            ALL_VECTORIZED=false
        fi
    done

    if [ "$ALL_VECTORIZED" = true ]; then
        break
    fi

    echo "$LOG_PREFIX Waiting ${VECT_INTERVAL}s for vectorization... (${VECT_ELAPSED}s elapsed)"
    sleep $VECT_INTERVAL
    VECT_ELAPSED=$((VECT_ELAPSED + VECT_INTERVAL))
done

if [ "$ALL_VECTORIZED" = true ]; then
    echo "$LOG_PREFIX All resumes vectorized in ${VECT_ELAPSED}s"
    run_test "Resume vectorization" "true"
else
    echo "$LOG_PREFIX Vectorization timeout after ${VECT_TIMEOUT}s"
    run_test "Resume vectorization" "false"
fi

# ============================================
# 6. AI Matching
# ============================================
echo "$LOG_PREFIX --- Step 6: AI Matching ---"
if [ -n "$JD_ID" ] && [ "$ALL_VECTORIZED" = true ]; then
    MATCH_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/match" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"jobId\":\"$JD_ID\",\"topK\":10}" \
        --max-time 120 \
        2>/dev/null || echo '{}')

    MATCH_CODE=$(echo "$MATCH_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")
    RESULTS_COUNT=$(echo "$MATCH_RESPONSE" | python3 -c "
import sys,json
d = json.load(sys.stdin)
results = d.get('data',{}).get('results',[])
print(len(results))
" 2>/dev/null || echo "0")

    if [ "$MATCH_CODE" = "200" ] && [ "$RESULTS_COUNT" -gt 0 ]; then
        echo "$LOG_PREFIX Matching returned $RESULTS_COUNT results"
        run_test "AI Matching ($RESULTS_COUNT results)" "true"

        # Print match details
        echo "$LOG_PREFIX --- Match Results ---"
        echo "$MATCH_RESPONSE" | python3 -c "
import sys,json
d = json.load(sys.stdin)
for r in d.get('data',{}).get('results',[]):
    print(f\"  Resume: {r.get('resumeName',r.get('resumeId','?'))}  LLM Score: {r.get('llmScore','?')}  Vector Score: {r.get('vectorScore','?'):.4f}\")
    print(f\"    Reasoning: {r.get('reasoning','N/A')[:200]}\")
"
    else
        echo "$LOG_PREFIX Matching failed or returned no results. Response: $MATCH_RESPONSE"
        run_test "AI Matching" "false"
    fi
else
    echo "$LOG_PREFIX Skipping matching (JD or vectorization not ready)"
    run_test "AI Matching" "false"
fi

# ============================================
# 7. AI Service Health
# ============================================
echo "$LOG_PREFIX --- Step 7: AI Service Health ---"
AI_PORT=$(echo "$BASE_URL" | sed 's/.*://' | sed 's/808.*/800&/' | sed 's/80\([0-9]\)2/80\12/')
# Derive AI service port from backend port
BACKEND_PORT=$(echo "$BASE_URL" | grep -oP ':\K\d+')
AI_PORT=$((BACKEND_PORT - 2))
AI_HOST=$(echo "$BASE_URL" | sed 's/:[0-9]*//' | sed 's|http://||')
AI_HEALTH=$(curl -sf "http://$AI_HOST:$AI_PORT/health" 2>/dev/null || echo "")
if [ -n "$AI_HEALTH" ]; then
    echo "$LOG_PREFIX AI service healthy: $AI_HEALTH"
    run_test "AI service health" "true"
else
    run_test "AI service health" "false"
fi

# ============================================
# Summary
# ============================================
echo "$LOG_PREFIX ===================================="
echo "$LOG_PREFIX Real-Data Verification Summary"
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
