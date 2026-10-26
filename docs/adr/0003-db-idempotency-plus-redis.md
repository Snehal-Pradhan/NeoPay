# ADR 0003 — Database uniqueness plus Redis marker for idempotency

**Status:** Accepted

## Context

Retries are unavoidable. A repeated `POST /v1/payments` must not create a second
charge.

## Decision

Two layers:

1. A unique index on `(merchant_id, idempotency_key)` is the authority. It is correct
   under concurrency and survives Redis loss.
2. A Redis `SETNX` marker with a 30-second TTL rejects concurrent duplicates early,
   before they reach the database.

The completed response is stored in Redis for 24 hours and replayed on a hit.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Redis only | A Redis flush or eviction window permits duplicate charges |
| Database only | Correct, but every concurrent duplicate pays the cost of a failed insert |
| Application-level in-memory map | Not shared across replicas |

## Consequences

- Correctness does not depend on Redis. Redis is an optimisation.
- The Redis marker can be lost; the database still rejects the duplicate.
- A conflicting payload under the same key returns `409 IDEMPOTENCY_CONFLICT`.
