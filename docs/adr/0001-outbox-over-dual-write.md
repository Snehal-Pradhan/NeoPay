# ADR 0001 — Transactional outbox over dual write

**Status:** Accepted

## Context

Payment state changes must reach Kafka so operations can fan out webhooks. A naive
implementation writes the database row, then publishes to Kafka. The two systems
cannot participate in one transaction.

## Decision

Write an `outbox_event` row in the same database transaction as the business change.
A poller publishes pending rows and marks them `PUBLISHED` or `FAILED`.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Publish inside the transaction | Kafka is not transactional with Postgres; a rollback after publish creates a phantom event |
| Publish after commit, best effort | Process crash between commit and publish loses the event silently |
| Change data capture (Debezium) | Adds a broker and connector lifecycle; correct, but heavier than needed at this scale |

## Consequences

- At-least-once delivery. Consumers must be idempotent.
- Outbox lag is a first-class metric (`outbox_lag_seconds`).
- A stuck poller delays webhooks but never loses money movement.
