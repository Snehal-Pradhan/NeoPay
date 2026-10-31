#!/usr/bin/env bash
#
# Dependency-kill experiment with measured recovery time.
#
# Usage:
#   ./scripts/chaos/run-chaos.sh postgres
#   ./scripts/chaos/run-chaos.sh kafka
#
# Requires: local stack running (scripts/run-local.sh), Docker Compose, k6 image,
# jq, curl. Produces perf/chaos-<target>.json with the measurements.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

TARGET="${1:-postgres}"
BASE="${BASE_URL:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
OUT="perf/chaos-${TARGET}.json"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"
OUTAGE_SECONDS="${OUTAGE_SECONDS:-20}"
RUN_SECONDS="${RUN_SECONDS:-90}"

say() { printf '%s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null 2>&1 || fail "jq is required"
curl -fsS -m 3 "$BASE/actuator/health" >/dev/null 2>&1 || fail "gateway not healthy at $BASE"

say "1. Baseline: 20 merchants and keys"
AUTH_LIST=()
for i in $(seq 1 20); do
  email="chaos-$(date +%s)-$i@example.test"
  merchant="$(curl -fsS -X POST "$BASE/v1/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"ChaosPass123!\",\"businessName\":\"Chaos $i\"}")"
  merchant_id="$(jq -r '.merchantId' <<<"$merchant")"
  key="$(curl -fsS -X POST "$BASE/v1/auth/keys" -H 'Content-Type: application/json' \
    -d "{\"merchantId\":\"$merchant_id\",\"email\":\"$email\"}")"
  AUTH_LIST+=("$(jq -r '.keyId' <<<"$key"):$(jq -r '.keySecret' <<<"$key")")
done
KEY_PAIR="${AUTH_LIST[0]}"

say "2. k6 load starts; $TARGET is stopped after 10s for ${OUTAGE_SECONDS}s"
docker run --rm -i --network host -e BASE_URL="$BASE" -e AUTH_LIST="$(IFS=,; echo "${AUTH_LIST[*]}")" \
  -e RATE=15 -e DURATION="${RUN_SECONDS}s" "$K6_IMAGE" run - < perf/k6-payment.js \
  > "perf/chaos-k6-${TARGET}.log" 2>&1 &
K6_PID=$!

sleep 10
STOP_AT="$(date +%s)"
say "3. Stopping $TARGET"
$COMPOSE stop "$TARGET" >/dev/null
sleep "$OUTAGE_SECONDS"
say "4. Starting $TARGET"
$COMPOSE start "$TARGET" >/dev/null

say "5. Measuring recovery"
RECOVERED_AT=""
for _ in $(seq 1 120); do
  status="$(curl -s -o /dev/null -w '%{http_code}' -m 2 "$BASE/actuator/health" || true)"
  if [[ "$status" == "200" ]]; then
    code="$(curl -s -o /dev/null -w '%{http_code}' -m 3 -X POST "$BASE/v1/orders" \
      -H 'Content-Type: application/json' -H 'X-Idempotency-Key: chaos-probe' \
      -H "X-Key-Id: ${KEY_PAIR%%:*}" -H "X-Key-Secret: ${KEY_PAIR##*:}" \
      -d '{"amountUnits":1000,"currency":"INR","receipt":"chaos-probe"}' || true)"
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

say "6. Duplicate-charge check"
DUPES="$(docker exec neopay-postgres psql -U postgres -d neopay-payment-db -t -A -c \
  "select count(*) from (select merchant_id, idempotency_key from payment group by 1,2 having count(*) > 1) d" 2>/dev/null || echo unknown)"

say "7. Outbox drain check"
PENDING="$(docker exec neopay-postgres psql -U postgres -d neopay-payment-db -t -A -c \
  "select count(*) from outbox_event where status = 'PENDING'" 2>/dev/null || echo unknown)"

jq -n --arg target "$TARGET" --argjson rto "$RTO" --argjson outage "$OUTAGE_SECONDS" \
  --arg dupes "$DUPES" --arg pending "$PENDING" \
  '{target:$target, outageSeconds:$outage, recoverySeconds:$rto, duplicateCaptureGroups:$dupes, pendingOutboxEvents:$pending}' \
  > "$OUT"

say "8. Result"
cat "$OUT"
