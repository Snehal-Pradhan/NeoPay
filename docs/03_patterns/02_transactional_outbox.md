# Transactional outbox

Domain events are written in the same database transaction as the state change, then relayed to Kafka by a poller. There is no dual write between Postgres and Kafka.

![Outbox pattern](../assets/03_outbox_pattern.png)

Diagram source: `docs/assets/scripts/03_outbox_pattern.py`.

## Write path

| Step | Action |
|---:|---|
| 1 | The service saves the aggregate row |
| 2 | `OutboxEventPublisher` inserts `outbox_event` with status `PENDING` in the same transaction |
| 3 | The transaction commits; both rows exist or neither does |

Source: `payment-service/src/main/java/com/snehal/neopay/payment/outbox/OutboxEventPublisher.java`.

## Relay path

| Step | Action |
|---:|---|
| 1 | `OutboxPoller` runs under a ShedLock lease, `lockAtMostFor` 1 minute |
| 2 | It selects `PENDING` rows ordered by creation |
| 3 | It publishes to the topic chosen from the aggregate type |
| 4 | `OutboxResultHandler` marks the row `PUBLISHED` or increments attempts |
| 5 | After the third failure the row becomes `FAILED` with the error text |

Topic defaults: `payments.events`, `orders.events`, `refunds.events`, `settlements.events`.

## Delivery guarantee

| Property | Value |
|---|---|
| Guarantee | At least once |
| Duplicate risk | A crash after publish and before the status update re-sends the event |
| Consumer requirement | Deduplicate on event id or a business key |
| Ordering | Per aggregate only in practice; not enforced across partitions |

## Failure states

| Status | Meaning | Recovery |
|---|---|---|
| `PENDING` | Awaiting relay | Automatic |
| `PUBLISHED` | Delivered to Kafka | None |
| `FAILED` | Three attempts failed | Operator inspects the error and resets to `PENDING` |

Runbook entry: [outbox stalled](../runbook.md).

## Why not CDC

| Option | Benefit | Cost |
|---|---|---|
| Polling relay (used) | No extra infrastructure; simple to test | Latency equals the poll interval; extra reads |
| Debezium | Lower latency; no application polling | Connector deployment, schema coupling, another failure domain |

## Evidence

| Check | Result |
|---|---|
| Chaos, Kafka stopped for 20 s | 476 pending rows, drained to zero within 20 s after restart |
| Chaos, Postgres stopped for 20 s | 155 pending rows, drained to zero within 30 s |
| Duplicate captures during both | Zero |

Measurements: [CHAOS](../CHAOS.md).

Related: [payment service](../02_services/payment_service.md), [resilience](04_resilience_circuit_breakers.md).

## Diagram script

```python
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka

with Diagram("Transactional outbox", show=False, direction="LR",
             filename="./docs/assets/03_outbox_pattern", outformat="png"):
    with Cluster("payment-service, one transaction"):
        svc = Server("domain write")
        payment = PostgreSQL("payment row")
        outbox = PostgreSQL("outbox_event\nPENDING")
    db = PostgreSQL("payment-db")
    with Cluster("Relay"):
        poller = Server("OutboxPoller\nShedLock 1m")
        handler = Server("KafkaPublisher")
        result = Server("OutboxResultHandler\nPUBLISHED / FAILED")
    bus = Kafka("kafka topic")

    svc >> Edge(label="insert") >> payment
    svc >> Edge(label="insert same tx") >> outbox
    payment - outbox
    outbox >> Edge(label="select PENDING") >> db
    db >> poller >> handler >> bus
    handler >> result >> outbox
```
