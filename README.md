# NeoPay — Microservices Payments Platform

> **Database note:** every entity now inherits an optimistic-lock `version` column from
> `BaseEntity`. An existing database needs `version bigint` added and backfilled with `0`
> before deploy. Schema management moves to Flyway in the platform phase.
>
> **Rollout status:** published incrementally, one project at a time. Currently in the
> repository: `common-lib` and `merchant-service`. The remaining services are being
> hardened and published in the same way.

A Spring Boot 4 / Java 21 payments platform: API gateway, service discovery, central configuration, a shared library, and four independently deployable business services (merchant, payment, vault, operations).


## Lineage

This project began as an extension of a public teaching reference implementation.
The domain shape (five services, gateway, config and discovery) comes from that
reference. Everything below that line is this repository's own work:

- the double-entry ledger model and `PaymentAggregate` invariants
- the event store, snapshots and upcasting
- the deterministic 10M-transaction simulator and its proof harness
- the idempotency layering (database index plus Redis marker)
- CI-grade quality gates: ArchUnit, PMD, SpotBugs, ErrorProne, PIT, JaCoCo
- the ADR set, runbooks, postmortems, C4 views and performance work

Packages use the `com.snehal.neopay` namespace throughout.

## Architecture

```text
                         ┌──────────────────────┐
                         │    API Gateway :8080 │
                         │  auth, routes, rate  │
                         └──────────┬───────────┘
                                    │
        ┌───────────────┬───────────┼────────────┬────────────────┐
        ▼               ▼           ▼            ▼                ▼
  merchant-service  payment-service  vault-service  operations-service
      :9010             :9020           :9040            :9030
        │                 │               │                │
  merchant DB        payment DB       vault DB       operations DB
        └─────────────────┴───────────────┴────────────────┘
                                    │
                     PostgreSQL · Redis · Kafka · Zipkin

  discovery-service :8761   (Eureka)
  config-service    :8888   (Spring Cloud Config, native local repo)
  common-lib                (shared DTOs, enums, security, errors,
                             rate limiting, idempotency, Kafka, Zipkin)
```

### Service responsibilities

| Service | Port | Database | Responsibility |
|---|---:|---|---|
| `api-gateway-service` | 8080 | — | Routing, Basic API-key auth, JWT auth, identity headers, rate limiting |
| `merchant-service` | 9010 | `neopay-merchant-db` | Signup/login, API keys, customers, webhook configs, internal lookups |
| `payment-service` | 9020 | `neopay-payment-db` | Orders, payments, idempotency, compensation, gateway/processor routing, outbox, simulator |
| `vault-service` | 9040 | `neopay-vault-db` | PAN tokenization, AES-GCM envelope encryption, token charging |
| `operations-service` | 9030 | `neopay-operations-db` | Settlements, bank transfers, webhook fan-out/retry/DLQ, its own outbox |
| `config-service` | 8888 | — | Spring Cloud Config Server using the bundled native config repo |
| `discovery-service` | 8761 | — | Eureka service registry |
| `common-lib` | — | — | Shared types and auto-configuration |

## Prerequisites

- Java 21
- Maven 3.10+
- Docker / Docker Desktop

## Run locally

```bash
# 1. Infrastructure (Postgres on 5433, Redis on 6380, Kafka on 29092, Zipkin on 9411)
docker compose up -d

# 2. Build common-lib and all services
./build-all.sh

# 3. Start discovery/config/services/gateway
./scripts/run-local.sh

# 4. Stop the application processes
./scripts/stop-local.sh

# 5. Stop infrastructure
docker compose down -v
```

The local ports differ from the service defaults because a local Postgres/Redis may already use `5432`/`6379`. `scripts/run-local.sh` exports the matching overrides automatically.

Health:

```bash
curl http://localhost:8888/actuator/health
curl http://localhost:8761/eureka/apps
curl http://localhost:8080/actuator/health
```

## Configuration

The Config Server uses the **native** profile and serves YAML from:

```text
config-service/src/main/resources/config/
├── application.yml
├── api-gateway-service.yml
├── merchant-service.yml
├── payment-service.yml
├── operations-service.yml
└── vault-service.yml
```

Services import it with `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`. This keeps the Pro architecture (central config + discovery + gateway) while avoiding the reference project's external GitHub config server and embedded credentials.

Production-style overrides are environment variables, including `JWT_SECRET`, `VAULT_MASTER_KEY`, `WEBHOOK_SECRET_ENCRYPTION_KEY`, per-service DB URLs/passwords, `REDIS_HOST/PORT`, and `KAFKA_BROKERS`.

## API surface (through the gateway)

| Method | Path | Service |
|---|---|---|
| POST | `/v1/auth/signup`, `/v1/auth/login` | merchant |
| POST/GET | `/v1/merchants/api-keys` | merchant |
| DELETE | `/v1/merchants/api-keys/{keyId}` | merchant |
| POST | `/v1/merchants/api-keys/{keyId}/rotate` | merchant |
| POST/GET/PUT/DELETE | `/v1/merchants/webhooks/**` | merchant |
| POST | `/v1/orders` | payment |
| POST | `/v1/payments`, `/v1/payments/{id}/capture` | payment |
| POST | `/v1/vault/tokenize` | vault |
| POST | `/webhook/success` | operations (public dummy receiver) |

## Tests

```bash
mvn -f common-lib/pom.xml install -DskipTests
for svc in discovery-service config-service api-gateway-service merchant-service payment-service vault-service operations-service; do
  mvn -f "$svc/pom.xml" test
done
```

132 tests across eight modules. Layers:

| Layer | Scope |
|---|---|
| Unit | services, domain, money, state machine, idempotency, rate limiting |
| Property | jqwik: money, settlement fees, state machine sequences |
| Integration | Testcontainers Postgres and Redis |
| Concurrency | 50 threads on `(merchant_id, idempotency_key)` |
| Contract | OpenAPI document shape |
| Architecture | ArchUnit package and parameter budgets |

Quality gates enforced on `verify`:

| Gate | Tool |
|---|---|
| Package boundaries, no field injection, ≤5 parameters | ArchUnit |
| Cyclomatic complexity ≤10, ≤25 methods, ≤20 fields, coupling ≤20 | PMD |
| High and medium correctness defects | SpotBugs |
| Compiler-level correctness | ErrorProne |
| Source file length ≤300 lines | `scripts/complexity-budget.sh` |
| Mutation coverage ≥80% on selected classes | PIT |

Measured line coverage: 24.8%. This is below the 80% target and is listed as an open
gate.

## Verified flows

A full local run was exercised through the gateway:

- signup → login → create API key
- create order
- tokenize a card in vault
- initiate card payment (payment → vault over Feign/Eureka)
- scheduled bank callback → `CAPTURED` + order `PAID`
- payment idempotency (`X-Idempotency-Key` returns the same payment, one DB row)
- outbox → Kafka → operations webhook fan-out → dummy receiver `204 DELIVERED`
- ShedLock keys in Redis
- gateway 401 for invalid/revoked credentials and structured error envelopes

## Intentional differences from the Pro reference

- **Java 21** instead of Java 25 (current build environment); all sources compile.
- **Native Config Server** bundled with this repo instead of the public GitHub config repo.
- **Sanitized secrets**; the reference's hardcoded GitHub PAT and real-looking keys are not included.
- **Bug fixes**: response status mappings, revoke route, Kafka listener keys, `NETBANKING` binding, local merchant webhook-key typo, and a serializable vault-charge response.
- **Tests** added for every service context; the Pro reference only had empty context tests.
- Jib image publishing is opt-in (`-Djib.skip=false`) and points at neutral `neopay/...` image names.

## Documentation

| Document | Contents |
|---|---|
| `docs/API.md` | Versioning, RFC 9457 errors, pagination, idempotency |
| `docs/DESIGN.md` | Invariants, consistency model, failure modes, weak points |
| `docs/SIMULATION.md` | Deterministic 10M-transaction simulation and results |
| `docs/POSTMORTEM.md` | Six real defects: symptom, root cause, fix, prevention |
| `docs/runbook.md` | Outbox stall, DLQ, Redis loss, Kafka lag, settlement failure |
| `docs/c4/` | Context, container, and payment component diagrams |
| `docs/adr/` | Eight architecture decision records |
| `ROADMAP.md` | Remaining engineering gates and scoring |
| `CHANGELOG.md` | Phase-by-phase history |

## Known gaps

- Refund HTTP API is not implemented. `PaymentAggregate` enforces refund invariants,
  but no controller exposes them.
- Wallet payments have no adapter or processor.
- Settlement uses one global fee schedule; per-merchant pricing is not wired.
- `Payment`, `OrderRecord` and `Settlement` remain the persistence projection. The
  aggregate is not yet the write path, and entity setters still exist.
- No cross-service end-to-end test covering settlement and webhook delivery in one run.
- Line coverage is 24.8% against an 80% target.
- Internal service endpoints are cluster-internal and unauthenticated; do not expose
  them directly.
- Kafka header trace propagation is not asserted by a test.
- Local compose starts Postgres, Redis, Kafka and Zipkin; Prometheus and Grafana run
  from `observability/`.
