# Idempotency

A retried request must not create a second payment, refund, ledger entry, or webhook side effect. NeoPay enforces this at three layers: the gateway, the database, and the state machine.

## Layers

| Layer | Scope | Mechanism | Failure behaviour |
|---|---|---|---|
| Gateway | Any `POST`, `PUT`, `PATCH` with `X-Idempotency-Key` | Redis placeholder, then stored response for 24 h | Redis loss fails open; no replay |
| Database | Payment and refund creation | Unique `(merchant_id, idempotency_key)` | Always enforced |
| State machine | Capture, settle, resolve | Status guard plus row lock | Second attempt is a no-op or conflict |
| Ledger | Posting | Unique `(transaction_id, account)` | Second post is skipped |
| Inbound webhook | Processor callback | Unique `(provider, event_id)` | Duplicate is acknowledged without processing |

## Gateway filter

| Property | Value |
|---|---|
| Placeholder TTL | 120 seconds |
| Completed TTL | 24 hours |
| Key space | `(merchant_id, key)` when a merchant is known |
| Conflict | A second in-flight request gets `409 IDEMPOTENCY_CONFLICT` |
| Storage | Redis string: `status|body` |

Source: `common-lib/src/main/java/com/snehal/neopay/common/idempotency/IdempotencyFilter.java`.

## Database enforcement

Two concurrent inserts with the same key race on the unique index. The loser receives a constraint violation, which the service translates into either a replay of the stored row or `409`.

| Test | Assert |
|---|---|
| `PaymentIdempotencyConcurrencyTest` | two threads, one key, one payment row |
| `RefundConcurrencyTest` | two threads, one key, one refund row |
| `DisputeConcurrencyTest` | two resolves, one ledger pair |

## Webhook replay

The event table insert runs in its own transaction. A duplicate delivery loses the unique race and returns `duplicate=true`; no state change and no ledger post occur.

## Known limits

| Limit | Effect |
|---|---|
| Gateway replay needs Redis | Without it, the request executes and relies on database uniqueness |
| Keys are never expired in the database | Storage grows with attempts |
| Payload comparison is not enforced on replay | The stored response is returned even if the body differs; only the in-flight case returns conflict |

Related: [webhooks](../WEBHOOKS.md), [payment service](../02_services/payment_service.md), [api gateway](../02_services/api_gateway.md).
