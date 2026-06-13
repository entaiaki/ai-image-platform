#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="${USERNAME:-e2e_user}"
PASSWORD="${PASSWORD:-Passw0rd!}"
PROMPT="${PROMPT:-E2E test cat}"

# Step0: register (ignore error if exists)
curl -sS -X POST "$BASE_URL/api/user/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" >/dev/null || true

# Step1: login -> token
TOKEN=$(curl -sS -X POST "$BASE_URL/api/user/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" | \
  python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

echo "TOKEN acquired"

# Step2: submit -> taskId
TASK_ID=$(curl -sS -X POST "$BASE_URL/api/tasks/submit" \
  --get --data-urlencode "prompt=$PROMPT" \
  -H "Authorization: Bearer $TOKEN" | \
  python -c "import sys,json;print(json.load(sys.stdin)['data']['taskId'])")

echo "Submitted taskId=$TASK_ID"

# Step3: poll until DONE/FAILED
for i in {1..60}; do
  RES=$(curl -sS "$BASE_URL/api/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN")
  STATUS=$(echo "$RES" | python -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")
  OUT_URL=$(echo "$RES" | python -c "import sys,json;d=json.load(sys.stdin)['data'];print(d.get('outputImageUrl') or '')")
  OUT_PATH=$(echo "$RES" | python -c "import sys,json;d=json.load(sys.stdin)['data'];print(d.get('outputLocalPath') or '')")

  echo "[$i] status=$STATUS url=$OUT_URL path=$OUT_PATH"

  if [[ "$STATUS" == "DONE" ]]; then
    if [[ -z "$OUT_URL" && -z "$OUT_PATH" ]]; then
      echo "E2E FAIL: DONE but output url/path empty" >&2
      exit 2
    fi
    echo "E2E PASS: DONE with output" 
    break
  fi

  if [[ "$STATUS" == "FAILED" ]]; then
    echo "E2E FAIL: task FAILED" >&2
    exit 3
  fi

  sleep 2

done

# Step4: my history contains taskId
HIS=$(curl -sS "$BASE_URL/api/tasks/my?limit=50" -H "Authorization: Bearer $TOKEN")
FOUND=$(echo "$HIS" | python -c "import sys,json;arr=json.load(sys.stdin)['data'];tid=int('$TASK_ID');print('1' if any(int(x['id'])==tid for x in arr) else '0')")

if [[ "$FOUND" != "1" ]]; then
  echo "E2E FAIL: taskId not found in /my" >&2
  exit 4
fi

echo "E2E PASS: /my contains taskId"
