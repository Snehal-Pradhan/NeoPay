# Database Volume and Query Plans

## Dataset

Local Docker Postgres 16, seeded on 2026-10-31.

| Table | Rows | Notes |
|---|---:|---|
| `ledger_line` | 1,100,176 | 1M seeded pairs plus soak traffic |
| `payment` | ~94,000 | captured payments from the soak |
| `order_record` | ~112,000 | one per payment |
| `outbox_event` | ~127,000 | soak events, most `PUBLISHED` |
| `webhook_event` | 0 | no live PSP traffic in this environment |

10M rows was the original target; the Docker volume sustained roughly 1.2k inserts/s, so the
run was cut at 1M. Extrapolation is stated as extrapolation, not as measurement.

## Method

`scripts/explain-volume.sh` runs `EXPLAIN (ANALYZE, BUFFERS, TIMING)` for six queries and
writes the raw plans to `docs/db/volume-plans.txt`. The pre-index file is
`docs/db/volume-plans-before-indexes.txt`.

## Results, cold-ish first run

| Query | Plan | Time |
|---|---|---:|
| Settlement candidates for one merchant | bitmap on `idx_payment_merchant_id`, then filter status/settled | 178 ms |
| Ledger replay check by `(transaction_id, account)` | index-only scan on the unique key | 34 ms |
| Global debit/credit aggregate | parallel sequential scan | 3.05 s |
| Unbalanced transaction detection | group by `transaction_id` | 4.8 ms |
| Webhook replay lookup | unique index | 0.06 ms |
| Outbox backlog (`status = 'PENDING'`) | index scan, 100 of 126k rows | 89 ms |

Second run after `V4__volume_indexes.sql` and a warm cache:

| Query | Time | Attribution |
|---|---:|---|
| Settlement candidates | 122 ms | composite index helps the filter; planner still starts from `merchant_id` |
| Ledger replay check | 0.1 ms | warm cache dominates |
| Global aggregate | 1.33 s | warm cache, not an index |
| Outbox backlog | 0.8 ms | existing composite index; the new partial index was not chosen |

Attribution matters: only the settlement number is plausibly index-driven. The rest is cache
warmth and must not be presented as an optimisation win.

## What the plans show

| Finding | Consequence |
|---|---|
| Global ledger aggregate is a full scan, O(rows) | the invariant checker is cheap now, expensive later |
| Settlement scan reads all of a merchant's payments, then filters | acceptable at 100 merchants, poor at 100k payments per merchant |
| Outbox backlog scan is fine while pending rows are few | degrades if a broker outage accumulates millions |
| Replay lookups are single-index | correct shape; latency is cache-bound |

## Proposed next step (not implemented)

| Change | Reason |
|---|---|
| `ledger_balance` summary table updated in the same transaction as each posting | turns the invariant check into O(1) |
| Monthly range partitions on `ledger_line (posted_at)` and `payment (created_at)` | keeps hot partitions small; archival by detach |
| Partial index `outbox_event (created_at) WHERE status = 'PENDING'` | already added, currently unused by the planner; revisit after accumulation |
| Retention job for `webhook_event` older than 30 days | bounded audit table |

Partitioning was not implemented: it would require rewriting Flyway baseline DDL and a
migration path for existing deployments, which is out of scope for a demonstration repository.
The plan above is the recommendation, not a claim of completed work.

## Reproduce

```bash
LINES=1000000 PAYMENTS=100000 ./scripts/seed-volume.sh
./scripts/explain-volume.sh
```
