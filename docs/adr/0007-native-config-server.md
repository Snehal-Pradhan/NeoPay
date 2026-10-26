# ADR 0007 — Native config server over a Git-backed config repository

**Status:** Accepted

## Context

The reference architecture used a Spring Cloud Config Server backed by a public Git
repository containing credentials. That is unsuitable for a portfolio repository and
adds an external dependency to every local run.

## Decision

Run Config Server with the `native` profile, loading YAML bundled under
`config-service/src/main/resources/config/`. Services import it with
`optional:configserver:...` so startup does not fail when the config server is absent.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Git-backed config server | Requires credentials in the repo or a private remote; slow feedback loop |
| Config in each service only | Duplicates shared settings and loses the central-config demonstration |
| Kubernetes ConfigMaps only | Couples configuration to one deployment target |

## Consequences

- The architecture is preserved without external dependencies.
- Configuration changes require a rebuild of `config-service`.
- Production overrides are environment variables.
