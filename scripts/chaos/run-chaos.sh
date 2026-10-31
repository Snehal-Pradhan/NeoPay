#!/usr/bin/env bash
#
# Dependency-kill experiment with measured recovery time.
#
#   ./scripts/chaos/run-chaos.sh postgres
#   ./scripts/chaos/run-chaos.sh kafka
#
# Requires: local stack running (scripts/run-local.sh), Docker Compose, k6 image,
# jq, curl, openssl. Writes perf/chaos-<target>.json.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

TARGET="${1:-postgres}"
BASE="${BASE_URL:-http://localhost:8080}"
K6_BASE="${K6_BASE:-http://host.docker.internal:8080}"
MERCHANT_INTERNAL="${MERCHANT_INTERNAL:-http://localhost:9010}"
PG_CONTAINER="${PG_CONTAINER:-neopay-ms-postgres}"
PG_USER="${PG_USER:-neopay}"
PG_DB="${PG_DB:-neopay-payment-db}"
OUT="perf/chaos-${TARGET}.json"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"
OUTAGE_SECONDS="${OUTAGE_SECONDS:-20}"
RUN_SECONDS="${RUN_SECONDS:-90}"
POOL_SIZE="${POOL_SIZE:-20}"

say() { printf '%s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
psql_value() { docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -c "$1" 2>/dev/null || echo unknown; }

command -v jq >/dev/null 2>&1 || fail "jq is required"
curl -fsS -m 3 "$BASE/actuator/health" >/dev/null 2>&1 || fail "gateway not healthy at $BASE"

say "1. Provisioning $POOL_SIZE merchants"
AUTH_LIST=()
STAMP="$(date +%s)"
for i in $(seq 1 "$POOL_SIZE"); do
  EMAIL="chaos-${STAMP}-${i}@example.com"
  SIGNUP=$(curl -fsS -X POST "$BASE/v1/auth/signup" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Chaos $i\",\"email\":\"$EMAIL\",\"password\":\"password123\",\"businessType\":\"LLP\"}") \
    || fail "signup failed"
  MERCHANT_ID=$(printf '%s' "$SIGNUP" | jq -r '.id // empty')
  curl -fsS -X POST "$MERCHANT_INTERNAL/internal/kyc/$MERCHANT_ID/verify" >/dev/null \
    || fail "kyc failed"
  curl -fsS -X POST "$MERCHANT_INTERNAL/internal/kyc/$MERCHANT_ID/tier" \
    -H 'Content-Type: application/json' -d '{"tier":"TIER_3"}' >/dev/null || fail "tier failed"
  TOKEN=$(curl -fsS -X POST "$BASE/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"password123\"}" | jq -r '.accessToken // empty')
  KEY=$(curl -fsS -X POST "$BASE/v1/merchants/api-keys" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d '{"environment":"TEST"}')
  AUTH_LIST+=("$(printf '%s:%s' "$(jq -r .keyId <<<"$KEY")" "$(jq -r .keySecret <<<"$KEY")" \
    | openssl base64 -A)")
done
AUTH_POOL=$(IFS=,; printf '%s' "${AUTH_LIST[*]}")
PROBE_KEY="${AUTH_LIST[0]}"
PROBE_CREDS=$(printf '%s' "$PROBE_KEY" | base64 -d)
PROBE_ID="${PROBE_CREDS%%:*}"
PROBE_SECRET="${PROBE_CREDS##*:}"

say "2. Starting k6 (${RUN_SECONDS}s); stopping $TARGET after 10s for ${OUTAGE_SECONDS}s"
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -e BASE_URL="$K6_BASE" -e AUTH_POOL="$AUTH_POOL" \
  -e RATE=15 -e DURATION="${RUN_SECONDS}s" "$K6_IMAGE" run - < perf/k6-payment.js \
  > "perf/chaos-k6-${TARGET}.log" 2>&1 &
K6_PID=$!

sleep 10
STOP_AT="$(date +%s)"
say "3. Stopping $TARGET"
docker compose stop "$TARGET" >/dev/null
sleep "$OUTAGE_SECONDS"
say "4. Starting $TARGET"
docker compose start "$TARGET" >/dev/null

say "5. Measuring recovery to a successful order"
RECOVERED_AT=""
for _ in $(seq 1 120); do
  if curl -fsS -m 2 "$BASE/actuator/health" >/dev/null 2>&1; then
    code="$(curl -s -o /dev/null -w '%{http_code}' -m 4 -u "$PROBE_ID:$PROBE_SECRET" \
      -X POST "$BASE/v1/orders" -H 'Content-Type: application/json' \
      -H 'X-Idempotency-Key: chaos-probe' \
      -d '{"amount":{"amountUnits":1000,"currency":"INR"},"receipt":"chaos-probe"}' || true)"
    if [[ "$code" == "200" || "$code" == "201" ]]; then
      RECOVERED_AT="$(date +%s)"
      break
    fi
  fi
  sleep 1
done
[[ -n "$RECOVERED_AT" ]] || fail "$TARGET did not recover within 120s"
RTO=$((RECOVERED_AT - STOP_AT))

wait "$K6_PID" || true

say "6. Invariant checks"
DUPES="$(psql_value "select count(*) from (select merchant_id, idempotency_key from payment group by 1,2 having count(*) > 1) d")"
PENDING="$(psql_value "select count(*) from outbox_event where status = 'PENDING'")"
LEDGER_DELTA="$(psql_value "select coalesce(sum(debit_units) - sum(credit_units), 0) from ledger_line")"

jq -n --arg target "$TARGET" --argjson rto "$RTO" --argjson outage "$OUTAGE_SECONDS" \
  --arg dupes "$DUPES" --arg pending "$PENDING" --arg delta "$LEDGER_DELTA" \
  --arg log "perf/chaos-k6-${TARGET}.log" \
  '{target:$target, outageSeconds:$outage, recoverySeconds:$rto,
    duplicateCaptureGroups:$dupes, pendingOutboxEvents:$pending,
    ledgerDebitCreditDelta:$delta, k6Log:$log}' > "$OUT"

say "7. Result"
cat "$OUT"
