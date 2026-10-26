# ADR 0008 — Staged event sourcing: aggregate now, persistence projection later

**Status:** Accepted, partially implemented

## Context

Money movement benefits from an append-only history and from invariants enforced in
one place. Fully replacing the relational write path with event sourcing is a large
refactor that would invalidate most existing tests.

## Decision

Introduce the domain aggregate and value objects first, without changing persistence:

- `PaymentAggregate` owns capture and refund invariants.
- `PaymentId`, `MerchantId`, `OrderId` are value objects.
- `Money` carries a currency scale.
- `Payment`, `OrderRecord` and `Settlement` remain the persistence projection.

Event sourcing, snapshots and upcasting are deferred until the aggregate is the write
path.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Big-bang event sourcing rewrite | Replaces the tested write path in one step; high regression risk |
| No aggregate at all | Leaves invariants scattered across services and impossible to test in isolation |
| Event sourcing only in a new service | Splits the money model across two ownership boundaries |

## Consequences

- Invariants are testable in isolation today.
- Two representations of state exist temporarily; they must not diverge.
- The deferred work is explicit, not accidental.
