# ADR 0004 — Saga compensation over distributed transactions

**Status:** Accepted

## Context

Payment initiation calls the vault service over HTTP, which is a remote operation
that can fail after local state has been written.

## Decision

Two-phase local transaction:

1. Record the attempt and transition to `AUTHORIZING` in transaction one.
2. Call the gateway outside any transaction.
3. Apply the result, or compensate a failure, in a new transaction.

Compensation writes `PAYMENT_AUTHORIZATION_COMPENSATED` and moves the payment to
`FAILED`.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| XA / two-phase commit across services | Operationally fragile; requires XA-capable resources; a coordinator becomes a single point of failure |
| Holding a database transaction open across the HTTP call | Connection pool exhaustion under load; lock held for the network round trip |
| No compensation | Leaves payments stuck in `AUTHORIZING` with no owner |

## Consequences

- The system is eventually consistent between attempt and outcome.
- Every failure path must be compensatable and tested.
- `AUTHORIZING` is a visible state and must be observable.
