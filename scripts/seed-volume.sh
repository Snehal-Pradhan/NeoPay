#!/usr/bin/env bash
# Seed a plausible volume for query-plan and soak tests.
#
#   PAYMENTS=1000000 LINES=10000000 ./scripts/seed-volume.sh
#
# Writes directly to Postgres. Use only against a disposable local database.
set -euo pipefail

PAYMENTS="${PAYMENTS:-1000000}"
LINES="${LINES:-10000000}"
CONTAINER="${CONTAINER:-neopay-postgres}"
DB="${DB:-neopay-payment-db}"

psql_run() { docker exec -i "$CONTAINER" psql -U postgres -d "$DB" -v ON_ERROR_STOP=1 -q "$@"; }

echo "seed: $LINES ledger lines, $PAYMENTS synthetic payments"
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

echo "seed: refreshing planner statistics"
psql_run -c "ANALYZE ledger_line;"
echo "seed: done"
