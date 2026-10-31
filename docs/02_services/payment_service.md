# payment-service

Owns the payment lifecycle from order to capture, the double-entry ledger, refunds, disputes, and inbound processor callbacks.

![payment flow](../assets/service_payment_flow.png)

## Scope

| In scope | Out of scope |
|---|---|
| Order creation and cancellation | Settlement execution |
| Authorization and capture | Merchant bank details |
| Refunds and partial refunds | Card storage |
| Disputes and evidence | Merchant webhook delivery |
| Ledger posting | Reporting UI |
| Inbound PSP webhook handling | Outbound merchant notifications |

Entry point: `payment-service/src/main/java/com/snehal/neopay/payment/`.

## API

| Method and path | Purpose | Idempotent |
|---|---|---|
| `POST /v1/orders` | Create an order | No |
| `POST /v1/payments` | Initiate a payment attempt | Yes, `X-Idempotency-Key` |
| `POST /v1/payments/{id}/capture` | Capture an authorized payment | Yes, by state |
| `POST /v1/payments/{id}/refunds` | Create a refund | Yes, `X-Idempotency-Key` |
| `GET /v1/payments/{id}/refunds` | List refunds | Not applicable |
| `POST /v1/payments/{pid}/disputes` | Open a dispute | No |
| `GET /v1/payments/{pid}/disputes` | List disputes | Not applicable |
| `POST /v1/payments/{pid}/disputes/{did}/evidence` | Upload evidence | Yes, external evidence id |
| `POST /v1/payments/{pid}/disputes/{did}/represent` | File representment | No |
| `POST /v1/payments/{pid}/disputes/{did}/resolve` | Close as `WON` or `LOST` | No |
| `POST /webhook/stripe` | Processor callback | Yes, event id |
| `GET /internal/payments/unsettled-captured` | Settlement input | Not applicable |
| `POST /internal/payments/mark-settled` | Mark payments settled | No |
| `POST /internal/ledger/**` | Settlement and payout postings | Yes, transaction key |

Request shapes follow the DTO records in `payment-service/src/main/java/com/snehal/neopay/payment/dto/`.

```json
POST /v1/payments
{
  "orderId": "0f8b1c2e-...",
  "method": "UPI",
  "methodDetails": { "vpa": "customer@okaxis" }
}
```

## Internal flow

1. `PaymentAuthorizationRecorder` locks the order row, validates status and expiry, enforces risk, and inserts the payment.
2. The state machine moves `CREATED -> AUTHORIZING` and writes a transition log plus a domain event.
3. `PaymentGatewayRouter` selects an adapter or processor by method and calls the processor boundary.
4. A synchronous result authorizes and captures; an asynchronous result arrives on the webhook endpoint.
5. Capture posts one balanced ledger pair and marks the order paid.
6. Every state change publishes an outbox event in the same transaction.

## State machine

| From | Event | To |
|---|---|---|
| `CREATED` | `AUTHORIZE_ATTEMPT` | `AUTHORIZING` |
| `AUTHORIZING` | `AUTHORIZE_SUCCESS` | `AUTHORIZED` |
| `AUTHORIZING` | `AUTHORIZE_FAIL` | `FAILED` |
| `AUTHORIZED` | `CAPTURE_REQUEST` | `CAPTURING` |
| `CAPTURING` | `CAPTURE_SUCCESS` | `CAPTURED` |
| `CAPTURING` | `CAPTURE_FAIL` | `AUTHORIZED` |
| `CAPTURED` | `REFUND_INIT` | `PARTIALLY_REFUNDED` |
| `CAPTURED` or `PARTIALLY_REFUNDED` | `SETTLE` | `SETTLED` |

Source: `payment-service/src/main/java/com/snehal/neopay/payment/statemachine/PaymentStateMachine.java`.

## Resilience

| Dependency | Failure handling |
|---|---|
| Merchant risk call | Circuit breaker fails open; counter incremented |
| Vault | Circuit breaker; card charge fails, other methods continue |
| Processor | Exception triggers compensation to `FAILED`; no orphaned `AUTHORIZING` row |
| Postgres | Transaction rollback; no partial posting |
| Kafka | Outbox retains events until the relay succeeds |
| Redis | Idempotency marker unavailable; database uniqueness still blocks duplicates |

## Error modes

| Code | Status | Cause |
|---|---|---|
| `ORDER_NOT_PAYABLE` | 400 | Order is paid or cancelled |
| `ORDER_EXPIRED` | 400 | `expiresAt` passed |
| `INVALID_STATE_TRANSITION` | 409 | Event not allowed from the current status |
| `IDEMPOTENCY_CONFLICT` | 409 | Same key, different payload |
| `PAYMENT_NOT_DISPUTABLE` | 400 | Status not captured or settled |
| `DISPUTE_EXCEEDS_CAPTURED` | 400 | Dispute amount above the payment |
| `EVIDENCE_WINDOW_CLOSED` | 400 | Evidence after the deadline |
| `EVIDENCE_NOT_SUBMITTED` | 400 | Representment without evidence |
| `WEBHOOK_SIGNATURE_INVALID` | 400 | HMAC mismatch |

## Diagram script

```python
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.queue import Kafka
from diagrams.onprem.network import Internet

with Diagram("payment-service flow", show=False, direction="LR",
             filename="./docs/assets/service_payment_flow", outformat="png"):
    with Cluster("payment-service"):
        api = Server("PaymentController\nOrderController\nDisputeController\nStripeWebhookController")
        saga = Server("PaymentAuthorizationRecorder\nSaga compensation")
        state = Server("PaymentStateMachine\nTransitionService")
        ledger = Server("LedgerService\ndouble entry")
        outbox = Server("OutboxEventPublisher")
        repo = Server("Repositories")
    db = PostgreSQL("payment-db")
    cache = Redis("Redis\nidempotency marker")
    bus = Kafka("kafka")
    psp = Internet("PSP / simulator")

    api >> saga >> state
    saga >> psp
    saga >> ledger >> repo
    saga >> outbox >> repo
    api >> cache
    api >> repo >> db
    db >> Edge(label="poller") >> bus
    psp >> Edge(label="signed webhook") >> api
```

Related: [webhooks](../WEBHOOKS.md), [chargebacks](../CHARGEBACKS.md), [saga](../03_patterns/01_saga_orchestration.md), [idempotency](../03_patterns/03_idempotency.md).
