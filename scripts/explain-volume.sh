#!/usr/bin/env bash
# Capture query plans against a seeded database.
#   PAYMENTS=1000000 LINES=10000000 ./scripts/seed-volume.sh
#   ./scripts/explain-volume.sh
set -euo pipefail

CONTAINER="${CONTAINER:-neopay-ms-postgres}"
DB="${DB:-neopay-payment-db}"
OUT="${OUT:-docs/db/volume-plans.txt}"

mkdir -p "$(dirname "$OUT")"
: > "$OUT"

run() {
  local label="$1"
  local sql="$2"
  {
    echo "=================================================================="
    echo "-- $label"
    echo "=================================================================="
    docker exec -i "$CONTAINER" psql -U neopay -d "$DB" -X -q -c "EXPLAIN (ANALYZE, BUFFERS, TIMING) $sql"
    echo
  } >> "$OUT"
}

MERCHANT="$(docker exec -i "$CONTAINER" psql -U neopay -d "$DB" -t -A -c \
  "select merchant_id from payment limit 1" | tr -d '[:space:]')"
TX="$(docker exec -i "$CONTAINER" psql -U neopay -d "$DB" -t -A -c \
  "select transaction_id from ledger_line limit 1" | tr -d '[:space:]')"

run "settlement candidate scan: captured payments for one merchant" \
  "select id, amount_units, captured_at from payment
   where merchant_id = '$MERCHANT' and status in ('CAPTURED','PARTIALLY_REFUNDED') and settled_at is null
   order by captured_at"
run "ledger replay check by transaction and credit account" \
  "select 1 from ledger_line where transaction_id = '$TX' and account = 'MERCHANT_PAYABLE'"
run "ledger balance aggregate (invariant checker)" \
  "select coalesce(sum(debit_units),0), coalesce(sum(credit_units),0) from ledger_line"
run "unbalanced transaction detection" \
  "select transaction_id from ledger_line group by transaction_id
   having sum(debit_units) <> sum(credit_units) limit 10"
run "webhook replay lookup" \
  "select id from webhook_event where provider = 'STRIPE' and external_event_id = 'evt_missing'"
run "outbox backlog scan" \
  "select id from outbox_event where status = 'PENDING' order by created_at limit 100"

echo "plans written to $OUT"
