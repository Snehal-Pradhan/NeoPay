# ADR 0002 — ShedLock over database advisory locks

**Status:** Accepted

## Context

Scheduled jobs (outbox poller, bank callback simulator, settlement engine, webhook
delivery) must not run concurrently across replicas.

## Decision

Use ShedLock with a Redis provider. Each job acquires a named lock with a bounded
`lockAtMostFor` and `lockAtLeastFor`.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Postgres advisory locks | Ties job coordination to one database; each service owns a different database |
| Single-replica scheduler deployment | Wastes a replica and creates a single point of failure |
| Quartz clustered mode | Heavier, requires its own schema, duplicates ShedLock's purpose |

## Consequences

- Lock ownership expires automatically, so a crashed node cannot deadlock a job.
- Clock skew between nodes can shorten or lengthen the effective lock window.
  `lockAtMostFor` must exceed the worst-case job duration.
- Redis becomes a coordination dependency; loss of Redis pauses scheduling, not
  request handling.
