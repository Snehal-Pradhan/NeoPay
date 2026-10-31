#!/usr/bin/env bash
# Provision ACTIVE merchants with TEST keys and print a comma-separated AUTH_POOL
# of base64(keyId:keySecret) values, matching perf/k6-payment.js and perf/k6-soak.js.
#
#   export AUTH_POOL=$(./perf/provision-keys.sh 60)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${BASE_URL:-http://localhost:8080}"
MERCHANT_INTERNAL="${MERCHANT_INTERNAL:-http://localhost:9010}"
POOL_SIZE="${1:-20}"
STAMP="$(date +%s)"
LIST=()

for i in $(seq 1 "$POOL_SIZE"); do
  EMAIL="pool-${STAMP}-${i}@example.com"
  SIGNUP=$(curl -fsS -X POST "$BASE/v1/auth/signup" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Pool $i\",\"email\":\"$EMAIL\",\"password\":\"password123\",\"businessType\":\"LLP\"}")
  MID=$(printf '%s' "$SIGNUP" | jq -r '.id')
  curl -fsS -X POST "$MERCHANT_INTERNAL/internal/kyc/$MID/verify" >/dev/null
  curl -fsS -X POST "$MERCHANT_INTERNAL/internal/kyc/$MID/tier" \
    -H 'Content-Type: application/json' -d '{"tier":"TIER_3"}' >/dev/null
  TOKEN=$(curl -fsS -X POST "$BASE/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"password123\"}" | jq -r '.accessToken')
  KEY=$(curl -fsS -X POST "$BASE/v1/merchants/api-keys" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d '{"environment":"TEST"}')
  LIST+=("$(printf '%s:%s' "$(jq -r .keyId <<<"$KEY")" "$(jq -r .keySecret <<<"$KEY")" | openssl base64 -A)")
done

(IFS=,; printf '%s' "${LIST[*]}")
