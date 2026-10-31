# Sharding and backups

No sharding, partitioning, or backup automation is implemented. This document records the measured behaviour at volume and the plan that the measurements justify.

## Measured baseline

Dataset: 1.1M `ledger_line` rows, about 94k payments, single Postgres 16 node. Plans in `docs/db/volume-plans.txt`, analysis in [DB_VOLUME](../DB_VOLUME.md).

| Query | Time | Shape |
|---|---:|---|
| Settlement candidates for one merchant | 122 ms | bitmap index then filter |
| Ledger replay lookup | 0.1 ms warm | unique index |
| Global debit/credit aggregate | 1.33 s warm | parallel sequential scan |
| Outbox backlog | 0.8 ms warm | index scan |

## Index strategy

| Index | Table | Justification |
|---|---|---|
| `ux_ledger_line_txn_account (transaction_id, account)` | `ledger_line` | idempotent posting |
| `idx_payment_merchant_idempotency (merchant_id, idempotency_key)` | `payment` | request replay |
| `idx_payment_merchant_status_captured (merchant_id, status, captured_at)` | `payment` | settlement scan |
| `uq_webhook_provider_event (provider, external_event_id)` | `webhook_event` | inbound replay guard |
| `uq_dispute_evidence_external (dispute_id, external_evidence_id)` | `dispute_evidence` | evidence replay |
| `idx_outbox_status_created (status, created_at)` | `outbox_event` | relay poll |

## Partitioning plan

Not implemented. The plan orders work by measured cost.

| Step | Change | Reason | Blocker |
|---|---|---|---|
| 1 | `ledger_balance` summary row updated in the posting transaction | removes the full-table aggregate from the invariant check | needs a backfill on existing data |
| 2 | Monthly range partitions on `ledger_line (posted_at)` | keeps the hot partition small; archive by detach | requires rewriting the Flyway baseline and a migration for existing deployments |
| 3 | Monthly range partitions on `payment (created_at)` | settlement queries stay within recent months | same as above |
| 4 | Retention job for `webhook_event` older than 30 days | bounds the audit table | needs a policy owner |

## Replication

| Capability | Status | Consequence |
|---|---|---|
| Streaming replica | Not provisioned | Reports and settlement share the primary |
| Read replica for reporting | Marked as future in the storage diagram | Under load, reports compete with payment writes |
| Cross-region replica | None | Regional loss means restore from backup |

## Backup and restore

| Item | Status |
|---|---|
| Automated backup | None in this repository |
| Point-in-time recovery | Available from Postgres WAL in any managed deployment, not configured here |
| Restore drill | Not performed |
| RPO and RTO numbers | Not measured; the failure experiments measure service recovery, not data loss |

## Recovery order

If a full restore is needed, the dependency order is:

1. `neopay-merchant-db`, because payments validate merchant state.
2. `neopay-payment-db`, because settlements read payments.
3. `neopay-operations-db`, because it references payment ids.
4. `neopay-vault-db`, because only new card charges need tokens.

Ledger consistency is checked after restore by `LedgerInvariantChecker`, which sums debits and credits and counts unbalanced transactions.

## What the chaos experiments do cover

`docs/CHAOS.md` records a 20 s Postgres stop: 28 s recovery to a successful order, zero duplicate captures, zero ledger delta, outbox drained within 30 s. That measures restart behaviour, not disk loss or restore time.

Related: [storage architecture](02_storage_architecture.md), [DB volume](../DB_VOLUME.md), [chaos](../CHAOS.md).
