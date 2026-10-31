# ER diagram and schema

Schemas are owned per service. The diagram shows the payment database in full and the other three databases by their principal tables.

```mermaid
erDiagram
    ORDER_RECORD ||--o{ PAYMENT : "order_id"
    PAYMENT ||--o{ REFUND : "payment_id"
    PAYMENT ||--o{ PAYMENT_TRANSITION_LOG : "payment_id"
    PAYMENT ||--o{ DISPUTE : "payment_id"
    DISPUTE ||--o{ DISPUTE_EVIDENCE : "dispute_id"
    PAYMENT ||--o| FX_RATE_LOCK : "payment_id"
    LEDGER_LINE }o--|| PAYMENT : "reference_id"
    OUTBOX_EVENT }o--|| PAYMENT : "aggregate_id"
    DOMAIN_EVENT }o--|| PAYMENT : "aggregate_id"
    DOMAIN_SNAPSHOT }o--|| PAYMENT : "aggregate_id"

    ORDER_RECORD {
        uuid id PK
        uuid merchant_id
        bigint amount_units
        varchar currency
        varchar order_status
        varchar receipt
        int attempts
        timestamptz expires_at
    }
    PAYMENT {
        uuid id PK
        uuid order_id FK
        uuid merchant_id
        bigint amount_units
        varchar currency
        varchar status
        smallint method
        varchar idempotency_key
        varchar processor_reference
    }
    REFUND {
        uuid id PK
        uuid payment_id FK
        bigint amount_units
        varchar status
        varchar idempotency_key
    }
    DISPUTE {
        uuid id PK
        uuid payment_id FK
        uuid merchant_id
        bigint amount_units
        varchar status
        varchar source_account
        timestamptz evidence_due_at
        timestamptz evidence_submitted_at
    }
    DISPUTE_EVIDENCE {
        uuid id PK
        uuid dispute_id FK
        varchar external_evidence_id
        varchar summary
        timestamptz submitted_at
    }
    LEDGER_LINE {
        uuid id PK
        uuid transaction_id
        varchar account
        bigint debit_units
        bigint credit_units
        varchar reference_type
        uuid reference_id
        timestamptz posted_at
    }
    OUTBOX_EVENT {
        uuid id PK
        varchar aggregate_type
        uuid aggregate_id
        varchar event_type
        jsonb payload
        varchar status
        int attempts
    }
```

Diagram source of truth: `payment-service/src/main/resources/db/migration/V1__baseline.sql` and `V2`, `V3`, `V4`.

## Keys and constraints that carry behaviour

| Table | Constraint | Behaviour it enforces |
|---|---|---|
| `order_record` | unique `(merchant_id, receipt)` | a merchant cannot reuse a receipt |
| `payment` | unique `(merchant_id, idempotency_key)` | one payment per key per merchant |
| `refund` | unique `(payment_id, idempotency_key)` | one refund per key per payment |
| `ledger_line` | unique `(transaction_id, account)` | replay of a posting is a no-op |
| `domain_event` | unique `(aggregate_id, sequence_number)` | append-only ordering per aggregate |
| `webhook_event` | unique `(provider, external_event_id)` | inbound PSP replay guard |
| `dispute_evidence` | unique `(dispute_id, external_evidence_id)` | idempotent evidence upload |
| `reserve_release` | unique `(source_line_id)` | a reserve is released once |

## Other databases

| Database | Principal tables | Owner |
|---|---|---|
| `neopay-merchant-db` | `merchant`, `app_user`, `api_key`, `customer`, `merchant_webhook_config`, `merchant_limit_usage` | merchant-service |
| `neopay-operations-db` | `settlement`, `settlement_payment`, `payout`, `recon_run`, `recon_break`, `dlq_event`, `webhook_event` | operations-service |
| `neopay-vault-db` | `vault_card`, `card_token` | vault-service |

## Schema rules

| Rule | Reason |
|---|---|
| Money is stored as integer minor units plus a currency code | No floating point in any money path |
| State machines store the enum name, not the ordinal | `payment.status` is `varchar(30)`; `payment.method` is `smallint` |
| Every state change writes `payment_transition_log` | Audit and event replay |
| Ledger rows are never updated or deleted | Append-only posting |
| Timestamps are `timestamp without time zone` in the baseline | The application supplies UTC values |

## Known schema weaknesses

| Weakness | Effect |
|---|---|
| `payment.method` is an ordinal `smallint` | Reordering the enum would corrupt meaning |
| No check constraint on `payment.amount_units > 0` | Validation is application-level only |
| `ledger_line` has no partitioning | Aggregates scan the whole table, see [DB_VOLUME](../DB_VOLUME.md) |
| `webhook_event` has no retention policy | Table grows without bound |

Related: [storage architecture](02_storage_architecture.md), [isolation and tenancy](03_isolation_and_tenancy.md).
