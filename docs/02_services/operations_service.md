# operations-service

Runs everything that happens after capture: settlement, payouts, reconciliation, and delivery of merchant webhooks.

<p align="center"><img src="../assets/service_operations_flow.png" width="60%" alt="operations flow"></p>

## Scope

| In scope | Out of scope |
|---|---|
| Settlement computation and persistence | Payment state transitions |
| Payout files and payout returns | Merchant authentication |
| Reconciliation of PSP and bank statements | Ledger ownership |
| Merchant webhook delivery and retry | Card data |
| ISO 20022 message generation | Bank connectivity |

Entry point: `operations-service/src/main/java/com/snehal/neopay/operations/`.

## API

| Method and path | Purpose |
|---|---|
| `GET /internal/settlements/**` | Settlement statements |
| `POST /internal/iso20022/**` | Generate or validate payment messages |
| `POST /internal/recon/**` | Run reconciliation, list breaks |
| `POST /internal/**` | Payout file and payout return |
| Kafka `payments.events`, `orders.events`, `refunds.events`, `settlements.events` | Webhook fan-out input |

Because these are internal endpoints, the gateway does not expose them to merchants.

## Internal flow

1. `SettlementEngine` wakes on a schedule, asks `merchant-service` for active merchants, and calls `SettlementTransactionExecutor` per merchant.
2. The executor loads unsettled captured payments from `payment-service`, resolves pricing, computes fees, and persists a settlement.
3. It requests bank details, initiates a transfer, and posts one ledger request with fee, GST, TDS, reserve and net.
4. `BankSettlementCallbackSimulator` resolves pending transfers locally.
5. Domain events reach `WebhookKafkaConsumer`, which writes delivery rows and enqueues retries.

## Pricing

Fees resolve from `merchant_fee_schedule` (default) and `merchant_method_pricing` (method overrides with effectivity). Arithmetic: [PRICING](../PRICING.md). Reserve is merchant-level risk and applies to the post-debt base.

## Resilience

| Dependency | Failure handling |
|---|---|
| payment-service | Settlement marked `FAILED`; no partial ledger post |
| merchant-service | Missing bank details fail the settlement with a data reason |
| Kafka | Webhook delivery delayed; rows stay queued |
| Merchant endpoint | Retries with backoff; exhausted rows enter the dead-letter path |
| Redis | Retry queue and ShedLock leases pause |

## Error modes

| Condition | Result |
|---|---|
| No eligible payments | No settlement row created |
| Net at or below zero | Amount carried forward; settlement recorded as processed |
| Bank transfer throws | `FAILED` with `BANK_TRANSFER_FAILED` |
| Duplicate settlement run | Resolved transfer is skipped when status is not `TRANSFER_PENDING` |

## Diagram script

```python
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_operations_flow", outformat="png"):
    bus = node(Kafka, "Kafka")
    hook = node(Server, "webhook worker")
    api = node(Server, "internal API")
    settle = node(Server, "settlement")
    recon = node(Server, "recon")
    repo = node(Server, "repositories")
    db = node(PostgreSQL, "operations-db")
    payment = node(Server, "payment")
    merchant = node(Server, "merchant")

    bus >> Edge(label="consume") >> hook >> repo
    api >> settle >> repo >> db
    api >> recon >> repo
    settle >> payment
    settle >> merchant
```

Related: [pricing](../PRICING.md), [outbox](../03_patterns/02_transactional_outbox.md), [runbook](../runbook.md).
