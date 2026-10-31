#!/usr/bin/env bash
# Seed a plausible volume for query-plan and soak tests.
#
#   PAYMENTS=1000000 LINES=10000000 ./scripts/seed-volume.sh
#
# Writes directly to Postgres. Use only against a disposable local database.
set -euo pipefail

PAYMENTS="${PAYMENTS:-1000000}"
LINES="${LINES:-10000000}"
ORDERS="${ORDERS:-$PAYMENTS}"
CONTAINER="${CONTAINER:-neopay-ms-postgres}"
DB="${DB:-neopay-payment-db}"

psql_run() { docker exec -i "$CONTAINER" psql -U neopay -d "$DB" -v ON_ERROR_STOP=1 -q "$@"; }

echo "seed: $LINES ledger lines, $ORDERS orders, $PAYMENTS payments"
psql_run <<SQL
INSERT INTO ledger_line (id, transaction_id, account, debit_units, credit_units, currency,
                         reference_type, reference_id, posted_at)
SELECT gen_random_uuid(),
       gen_random_uuid(),
       (ARRAY['CUSTOMER_FUNDS','MERCHANT_PAYABLE'])[1 + (g % 2)],
       CASE WHEN g % 2 = 0 THEN 100 ELSE 0 END,
       CASE WHEN g % 2 = 1 THEN 100 ELSE 0 END,
       'INR',
       'SEED',
       gen_random_uuid(),
       now() - (g || ' seconds')::interval
FROM generate_series(1, $LINES) AS g;
SQL

if [[ "${SEED_API_ROWS:-1}" == "1" ]]; then
psql_run <<SQL
INSERT INTO order_record (id, version, amount_units, currency, attempts, expires_at,
                          merchant_id, order_status, receipt, created_at)
SELECT gen_random_uuid(), 0, 2500, 'INR', 1, now() + interval '1 day',
       md5((g % 100)::text)::uuid, 'PAID', 'seed-' || g,
       now() - (g || ' seconds')::interval
FROM generate_series(1, $ORDERS) AS g;

INSERT INTO payment (id, version, amount_units, currency, idempotency_key, merchant_id,
                     method, status, order_id, created_at, captured_at)
SELECT gen_random_uuid(), 0, o.amount_units, o.currency, 'seed-pay-' || o.receipt,
       o.merchant_id, 1, 'CAPTURED', o.id, o.created_at, o.created_at
FROM order_record o
WHERE o.receipt LIKE 'seed-%'
LIMIT $PAYMENTS;
SQL
fi

echo "seed: refreshing planner statistics"
psql_run -c "ANALYZE ledger_line; ANALYZE payment; ANALYZE order_record;"
echo "seed: done"
