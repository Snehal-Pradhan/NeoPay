#!/usr/bin/env bash
#
# Provision a pool of ACTIVE merchants with TEST API keys, then run the k6
# end-to-end payment benchmark against the local stack.
#
# Requirements: the seven services are running (scripts/run-local.sh), Docker is
# available, curl and jq are installed.
#
# Usage:
#   ./perf/run-k6.sh
#   POOL_SIZE=20 RATE=15 DURATION=60s ./perf/run-k6.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE="${BASE_URL:-http://localhost:8080}"
MERCHANT_INTERNAL="${MERCHANT_INTERNAL:-http://localhost:9010}"
POOL_SIZE="${POOL_SIZE:-20}"
RATE="${RATE:-15}"
DURATION="${DURATION:-60s}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"

say() { printf '%s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null 2>&1 || fail "jq is required"

say "1. Checking gateway health at $BASE"
for _ in $(seq 1 30); do
  curl -fsS -m 2 "$BASE/actuator/health" >/dev/null 2>&1 && break
  sleep 2
done
curl -fsS -m 2 "$BASE/actuator/health" >/dev/null 2>&1 || fail "gateway is not healthy at $BASE"

say "2. Provisioning $POOL_SIZE ACTIVE merchants and TEST API keys"
AUTH_LIST=()
STAMP="$(date +%s)"
for i in $(seq 1 "$POOL_SIZE"); do
  EMAIL="k6-merchant-${STAMP}-${i}@example.com"
  PASSWORD="password123"

  SIGNUP=$(curl -fsS -X POST "$BASE/v1/auth/signup" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"K6 Merchant $i\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"businessType\":\"LLP\"}") \
    || fail "signup failed for $EMAIL"
  MERCHANT_ID=$(printf '%s' "$SIGNUP" | jq -r '.id // empty')
  [ -n "$MERCHANT_ID" ] || fail "signup returned no merchant id: $SIGNUP"

  # Merchant-service starts merchants in PENDING_KYC. Activate and raise the tier
  # so risk checks cannot reject or step up the benchmark traffic.
  curl -fsS -X POST "$MERCHANT_INTERNAL/internal/kyc/$MERCHANT_ID/verify" >/dev/null \
    || fail "KYC verification failed for $MERCHANT_ID"
  curl -fsS -X POST "$MERCHANT_INTERNAL/internal/kyc/$MERCHANT_ID/tier" \
    -H 'Content-Type: application/json' \
    -d '{"tier":"TIER_3"}' >/dev/null \
    || fail "tier upgrade failed for $MERCHANT_ID"

  TOKEN=$(curl -fsS -X POST "$BASE/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r '.accessToken // empty')
  [ -n "$TOKEN" ] || fail "login failed for $EMAIL"

  KEY=$(curl -fsS -X POST "$BASE/v1/merchants/api-keys" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"environment":"TEST"}')
  KEY_ID=$(printf '%s' "$KEY" | jq -r '.keyId // empty')
  KEY_SECRET=$(printf '%s' "$KEY" | jq -r '.keySecret // empty')
  [ -n "$KEY_ID" ] && [ -n "$KEY_SECRET" ] || fail "API key creation failed: $KEY"

  AUTH_LIST+=("$(printf '%s:%s' "$KEY_ID" "$KEY_SECRET" | openssl base64 -A)")
done

AUTH_POOL=$(IFS=,; printf '%s' "${AUTH_LIST[*]}")
say "   provisioned ${#AUTH_LIST[@]} keys"

say "3. Running k6: $RATE flows/sec for $DURATION"
docker run --rm -i \
  --add-host=host.docker.internal:host-gateway \
  -v "$ROOT/perf:/perf" \
  -e BASE_URL="http://host.docker.internal:8080" \
  -e AUTH_POOL="$AUTH_POOL" \
  -e RATE="$RATE" \
  -e DURATION="$DURATION" \
  "$K6_IMAGE" run --summary-export=/perf/k6-summary.json /perf/k6-payment.js \
  | tee perf/k6-output.txt

say "4. Summary written to perf/k6-summary.json"
