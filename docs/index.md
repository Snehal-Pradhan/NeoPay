# NeoPay documentation

Master index. Each topic has one owner document; this page only routes.

## Architecture

| Document | Content |
|---|---|
| [System context](00_architecture/01_system_context.md) | Actors, trust boundaries, data ownership |
| [Container architecture](00_architecture/02_container_architecture.md) | Seven services, ports, sync and async paths |
| [Tech stack matrix](00_architecture/03_tech_stack_matrix.md) | Versions and the file that declares each |
| [Tech stack alternatives](00_architecture/04_tech_stack_alternatives.md) | Trade-offs against common production choices |

## Data

| Document | Content |
|---|---|
| [ER diagram and schema](01_data/01_er_diagram_and_schema.md) | Entities, keys, constraints that carry behaviour |
| [Storage architecture](01_data/02_storage_architecture.md) | Postgres, Redis, Kafka roles |
| [Isolation and tenancy](01_data/03_isolation_and_tenancy.md) | Merchant scoping and its gaps |
| [Sharding and backups](01_data/04_sharding_and_backups.md) | Measured volume, partition plan, restore order |

## Services

| Document | Content |
|---|---|
| [Service map](02_services/overview.md) | Boundaries, communication, failure isolation |
| [api-gateway-service](02_services/api_gateway.md) | Auth, throttling, routing |
| [auth module](02_services/auth_service.md) | Signup, login, API keys |
| [merchant-service](02_services/merchant_service.md) | Merchants, KYC, limits, webhook config |
| [payment-service](02_services/payment_service.md) | Orders, payments, refunds, disputes, ledger, webhooks |
| [operations-service](02_services/operations_service.md) | Settlement, payouts, reconciliation, delivery |
| [vault-service](02_services/vault_service.md) | Tokens and encrypted PANs |
| [config-service](02_services/config_service.md) | Configuration layout |
| [discovery-service](02_services/discovery_service.md) | Eureka registry |

## Patterns

| Document | Content |
|---|---|
| [Saga orchestration](03_patterns/01_saga_orchestration.md) | Payment steps and compensation |
| [Transactional outbox](03_patterns/02_transactional_outbox.md) | Write path, relay, delivery guarantee |
| [Idempotency](03_patterns/03_idempotency.md) | Gateway, database, state machine, ledger |
| [Resilience](03_patterns/04_resilience_circuit_breakers.md) | Breakers, fail-open policy, rate limits |

## Operations

| Document | Content |
|---|---|
| [Observability](04_operations/01_observability.md) | Traces, metrics, logs, gaps |
| [Deployment and scaling](04_operations/02_deployment_and_hpa.md) | CI, cluster shape, scaling limits |
| [Configuration reference](REFERENCE_CONFIGURATION.md) | Every config key and its value, generated from YAML |

## Behaviour and evidence

| Document | Content |
|---|---|
| [Performance](PERFORMANCE.md) | Load and soak numbers, method, limits |
| [Chaos](CHAOS.md) | Dependency-kill experiments and recovery |
| [DB volume](DB_VOLUME.md) | Query plans and index decisions |
| [Chargebacks](CHARGEBACKS.md) | Dispute lifecycle |
| [Webhooks](WEBHOOKS.md) | Inbound PSP contract |
| [Pricing](PRICING.md) | Fee resolution |
| [PCI scope](PCI_SCOPE.md) | SAQ-A argument and gates |
| [Runbook](runbook.md) | Symptom-first procedures |
| [Simulation](SIMULATION.md) | Deterministic lifecycle simulation |
| [Postmortems](POSTMORTEM.md) | Six defects and their prevention |
| [API contract](API.md) | Versioning, errors, pagination, idempotency |
| [Design](DESIGN.md) | Invariants and weak points |
| [Testing](../TESTING.md) | Test layers, coverage and mutation floors |
| [Contributing](../CONTRIBUTING.md) | Build, test, PR rules, commit format |
| [Security policy](../SECURITY.md) | Reporting, scope, design controls |

## Decisions

| ADR | Decision |
|---|---|
| [0001](adr/0001-outbox-over-dual-write.md) | Outbox instead of dual writes |
| [0002](adr/0002-shedlock-over-advisory-locks.md) | ShedLock for job mutual exclusion |
| [0003](adr/0003-db-idempotency-plus-redis.md) | Database uniqueness plus Redis marker |
| [0004](adr/0004-saga-compensation.md) | Compensation for failed authorization |
| [0005](adr/0005-database-per-service.md) | One database per service |
| [0006](adr/0006-tokenization-for-pci-scope.md) | Tokenisation boundary |
| [0007](adr/0007-native-config-server.md) | Native config server |
| [0008](adr/0008-staged-event-sourcing.md) | Aggregate first |
| [0009](adr/0009-signed-inbound-webhooks.md) | Signed webhooks with an event table |
| [0010](adr/0010-evidence-window-and-auto-loss.md) | Evidence deadline |
| [0011](adr/0011-per-method-effective-pricing.md) | Method pricing with effectivity |
| [0012](adr/0012-pci-scope-as-a-test.md) | PCI scope as a build gate |

## Diagrams

| Asset | Source |
|---|---|
| `assets/00_container_architecture.png` | `assets/scripts/00_container_architecture.py` |
| `assets/01_storage_architecture.png` | `assets/scripts/01_storage_architecture.py` |
| `assets/03_outbox_pattern.png` | `assets/scripts/03_outbox_pattern.py` |
| `assets/service_*_flow.png` | `assets/scripts/services/*.py` |
| `diagrams/architecture-overview.svg` | `diagrams/*.eraser` |
