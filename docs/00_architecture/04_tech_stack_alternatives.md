# Tech stack alternatives

Each table separates the common industry choice from the choice this repository makes, and states the cost of the local choice.

## Runtime and framework

| Option | Common in production | Used here | Why | Cost |
|---|---|---|---|---|
| Framework | Spring Boot | Spring Boot 4.1.0 | Existing ecosystem fit: Cloud, Data, Security, Actuator in one BOM | Higher startup time than Quarkus or Micronaut |
| Language | Java 21 | Java 21 | Records and sealed interfaces model payment results cleanly | No virtual-thread tuning evidence |
| Service mesh | Istio or Linkerd | none | Kubernetes network policies and gateway auth cover the demonstration scale | No mTLS between services |
| Gateway | Kong, Envoy, AWS API Gateway | Spring Cloud Gateway WebMVC | Same JVM stack, route tests in-process | Fewer plugins than Kong; WebMVC gateway has no NLB-grade throughput evidence |

## Data

| Option | Common in production | Used here | Why | Cost |
|---|---|---|---|---|
| Primary store | Postgres or MySQL with managed HA | Postgres 16, single node | Constraints carry idempotency and ledger balance | No failover, no read replica |
| Cache | Redis cluster | Redis 7, single node | Rate limit and idempotency markers only | Cache loss degrades throttle accuracy, not correctness |
| Object storage | S3 for statements and evidence | none | Reports are generated on demand | Dispute evidence is a summary string, not a document |
| Search | Elasticsearch | none | No free-text requirement | Report listing is SQL only |
| Ledger store | Dedicated ledger DB or event store | Postgres append-only table | Transactional pairing with payment writes | Aggregates scan the table |

## Messaging

| Option | Common in production | Used here | Why | Cost |
|---|---|---|---|---|
| Broker | Kafka or Pulsar with replication 3 | Kafka single broker, replication 1 | Fan-out for webhooks only | Broker loss stalls delivery, not payment writes |
| CDC | Debezium on the outbox table | Polling relay (`OutboxPoller`) | No connector deployment | Latency equals poll interval; extra read load |
| Delivery guarantee | Exactly-once via transactions | At-least-once plus idempotent consumer keys | Simpler broker configuration | Consumers must deduplicate |

## Delivery and operations

| Option | Common in production | Used here | Why | Cost |
|---|---|---|---|---|
| Orchestration | EKS or GKE with ArgoCD | `kind` smoke in CI | Proves image and manifest correctness without cloud cost | No promotion, no rollback drill |
| IaC | Terraform or Crossplane | manifests in `k8s/` | Repository runs anywhere with kind | No managed databases or networks |
| Secrets | External Secrets plus Vault | Kubernetes Secret from an env file | Local reproducibility | Secret rotation is manual |
| Autoscaling | HPA on latency and queue depth | deployment replicas fixed | No load evidence to size HPA correctly | No autoscaling proof |

## Latency and cost comparison

Numbers are order-of-magnitude only. **[estimated]** They are not measured in this repository.

| Choice | Added latency | Operational cost |
|---|---|---|
| Polling relay vs CDC | equal to poll interval, default 1 s | Low; no connector |
| Gateway rate limit in Redis | one Redis round trip per request | Low |
| Synchronous merchant risk call | one HTTP call per payment initiation | Medium; mitigated by circuit breaker fail-open |
| Kafka fan-out vs direct HTTP to merchant | decoupled; retries survive merchant downtime | Medium; broker to operate |

## Where the local choice would break

| Component | Break point | First change |
|---|---|---|
| Single Postgres | Disk or node loss | Managed HA instance, then read replica for reports |
| Polling outbox | Above roughly 1k events/s | CDC or batched relay |
| Single Kafka broker | Broker restart | Replication factor 3 and idempotent producer |
| In-memory rate limit window | Multi-replica gateway | Shared Redis counter with atomic increment per window |
| No service mesh | Compliance requirement for mTLS | Linkerd with automatic mTLS |

Related: [tech stack matrix](03_tech_stack_matrix.md), [sharding and backups](../01_data/04_sharding_and_backups.md).
