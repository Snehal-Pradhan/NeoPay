# ADR 0005 — One database per service

**Status:** Accepted

## Context

Merchant, payment, vault and operations data have different access patterns,
retention rules and failure impact.

## Decision

Each service owns one database. No service reads another service's tables. Cross-service
reads go through internal REST endpoints.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Shared schema | Couples deployments; a migration in one service can break another |
| Shared schema with views | Still couples migration timing and creates hidden dependencies |
| Direct cross-service SQL | Bypasses ownership, makes the schema a public API |

## Consequences

- Referential integrity across services is enforced by application logic, not foreign keys.
- Aggregations that span services need an API call or a projection.
- Local development needs four databases; the compose file provisions them.
