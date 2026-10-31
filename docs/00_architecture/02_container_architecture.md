# Container architecture

Seven Spring Boot services run as separate JVMs. Four own data, three provide edge and platform functions. Services communicate over HTTP with discovery, or asynchronously through Kafka.

![Container architecture](../assets/00_container_architecture.png)

Diagram source: `docs/assets/scripts/00_container_architecture.py`.

## Containers

| Container | Port | Responsibility | Data store |
|---|---:|---|---|
| `api-gateway-service` | 8080 | Routing, API key and JWT validation, rate limiting, idempotency filter | Redis |
| `discovery-service` | 8761 | Eureka registry | none |
| `config-service` | 8888 | Native configuration server | classpath `config/` |
| `merchant-service` | 9010 | Merchants, users, API keys, KYC, limits, merchant webhook configuration | Postgres |
| `payment-service` | 9020 | Orders, payments, refunds, disputes, ledger, outbox, inbound PSP webhooks | Postgres |
| `operations-service` | 9030 | Settlement, payouts, reconciliation, merchant webhook delivery | Postgres |
| `vault-service` | 9040 | Card tokenization and detokenization | Postgres |

## Synchronous paths

| Caller | Callee | Purpose |
|---|---|---|
| gateway | any service | Route by path prefix |
| payment | merchant | Risk limits and merchant status |
| payment | vault | Detokenize for a card charge |
| operations | payment | Unsettled captured payments; ledger postings; mark settled |
| operations | merchant | Settlement bank details; active merchant list |
| gateway | merchant | API key lookup, cached in Redis |

## Asynchronous paths

| Producer | Topic | Consumer |
|---|---|---|
| payment outbox | `payments.events`, `orders.events`, `refunds.events` | operations webhook fan-out |
| operations outbox | `settlements.events` | operations webhook fan-out |
| operations | merchant webhook URL | merchant backend, signed |

Topic defaults are declared in `operations-service/src/main/java/com/snehal/neopay/operations/webhook/WebhookKafkaConsumer.java`.

## Why this shape

| Decision | Reason | Reference |
|---|---|---|
| Gateway terminates merchant authentication | One place to enforce key and JWT rules | [api_gateway](../02_services/api_gateway.md) |
| Four services own money state | Bounded contexts around order, payment, settlement, card data | [service map](../02_services/overview.md) |
| Kafka only for fan-out | No service depends on Kafka for its write path | [outbox pattern](../03_patterns/02_transactional_outbox.md) |
| Config server instead of environment sprawl | One file per service per profile | [config service](../02_services/config_service.md) |

## Deployment shape

Local development runs jars directly (`scripts/run-local.sh`). CI builds Jib images and deploys `config-service` to a `kind` cluster to prove image and manifest correctness. The topology below production scale is not part of this repository.

Related: [system context](01_system_context.md), [deployment](../04_operations/02_deployment_and_hpa.md).

## Diagram script

```python
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.client import Users, Client
from diagrams.onprem.network import Internet
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.queue import Kafka
from diagrams.onprem.monitoring import Prometheus, Grafana

with Diagram("NeoPay container architecture", show=False, direction="LR",
             filename="./docs/assets/00_container_architecture", outformat="png"):
    users = Users("Merchants and customers")
    psp = Internet("Card and UPI processor")

    with Cluster("Edge"):
        gateway = Server("api-gateway\n:8080")
        discovery = Server("discovery\n:8761")
        config = Server("config\n:8888")

    with Cluster("Core services"):
        merchant = Server("merchant\n:9010")
        payment = Server("payment\n:9020")
        operations = Server("operations\n:9030")
        vault = Server("vault\n:9040")

    with Cluster("Data"):
        pg = PostgreSQL("Postgres\none database per service")
        cache = Redis("Redis\nrate limit, idempotency")
        bus = Kafka("Kafka\ndomain events")

    with Cluster("Observability"):
        zipkin = Prometheus("Zipkin")
        metrics = Grafana("Prometheus + Grafana")

    users >> Edge(label="API key / JWT") >> gateway
    gateway >> Edge(label="route") >> merchant
    gateway >> Edge(label="route") >> payment
    gateway >> Edge(label="route") >> operations
    gateway >> Edge(label="route") >> vault
    gateway >> Edge(label="public") >> psp

    merchant >> pg
    payment >> pg
    operations >> pg
    vault >> pg

    gateway >> cache
    payment >> cache
    payment >> Edge(label="outbox") >> bus
    operations >> bus
    payment >> Edge(label="webhook") >> psp

    gateway >> discovery
    merchant >> discovery
    payment >> discovery
    operations >> discovery
    vault >> discovery

    merchant >> config
    payment >> config
    operations >> config
    vault >> config

    payment >> Edge(style="dashed") >> zipkin
    operations >> Edge(style="dashed") >> metrics
```
