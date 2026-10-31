# merchant-service

Owns merchant identity, commercial profile, KYC state, risk limits, customers, and merchant webhook configuration. It is the authority other services consult for merchant status and bank details.

![merchant flow](../assets/service_merchant_flow.png)

## Scope

| In scope | Out of scope |
|---|---|
| Merchant record and status | Authentication policy enforcement |
| API key metadata | Payment decisions |
| KYC verification state | Settlement execution |
| Risk tier and limit usage | Card data |
| Customer records | Ledger entries |
| Merchant webhook configuration | Webhook delivery |

Entry point: `merchant-service/src/main/java/com/snehal/neopay/merchant/`.

## API

| Method and path | Purpose |
|---|---|
| `POST /v1/merchants/api-keys` | Create a key pair |
| `GET /v1/merchants/api-keys` | List keys |
| `POST /v1/merchants/api-keys/{keyId}/rotate` | Rotate a key |
| `POST /v1/merchants/webhooks` | Register a delivery URL |
| `GET /v1/merchants/webhooks/{id}` | Read a registration |
| `GET /internal/merchants/active-ids` | Merchant ids for settlement |
| `GET /internal/merchants/{id}/settlement-bank-details` | Bank account and IFSC |
| `GET /internal/merchants/{id}/webhook-targets` | Delivery targets |
| `POST /internal/customers/find-or-create` | Resolve a customer |
| `GET /internal/api-keys/{keyId}` | Key lookup for the gateway |
| `POST /internal/kyc/{id}/verify` | Set KYC verified |
| `POST /internal/kyc/{id}/tier` | Set risk tier |
| `POST /internal/risk/authorize` | Risk decision for a payment |

## Internal flow

1. A write validates the request, loads or creates the entity, and saves it.
2. The write publishes an outbox event in the same transaction.
3. `OutboxPoller` drains `PENDING` rows to Kafka.
4. Read endpoints serve straight from the repository.

## Resilience

| Dependency | Failure behaviour |
|---|---|
| Postgres | Requests fail; no partial write |
| Kafka | Outbox grows; writes still commit |
| Redis | API key cache misses fall back to Postgres |

`RiskEnforcer` in `payment-service` treats this service as fail-open: an unavailable merchant service increments a counter and allows the payment.

## Error modes

| Condition | Response |
|---|---|
| Unknown merchant | `404 MERCHANT_NOT_FOUND` |
| Missing bank details at settlement | Settlement marked `FAILED` with a data reason |
| Inactive merchant | Payment initiation rejected by the risk check |

## Diagram script

```python
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka

with Diagram("merchant-service flow", show=False, direction="LR",
             filename="./docs/assets/service_merchant_flow", outformat="png"):
    with Cluster("merchant-service"):
        api = Server("MerchantController\nKycController\nWebhookConfigController")
        svc = Server("MerchantService\nKycService\nLimitService")
        outbox = Server("OutboxEventPublisher")
        repo = Server("Repositories")
    db = PostgreSQL("merchant-db")
    bus = Kafka("kafka")

    api >> svc >> repo >> db
    svc >> outbox >> db
    db >> Edge(label="poller") >> bus
```

Related: [auth module](auth_service.md), [service map](overview.md).
