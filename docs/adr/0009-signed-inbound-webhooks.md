# ADR 0009: Signed inbound PSP webhooks with a durable event table

**Status:** Accepted

## Context

Card and UPI authorizations complete asynchronously. The processor posts the result to NeoPay
after the charge call returns. That callback is unauthenticated at the network edge, can be
redelivered, and can arrive out of order.

## Decision

`payment-service` exposes `POST /webhook/stripe`, public at the gateway and authenticated only
by the `Stripe-Signature` HMAC.

- Signed payload is `<timestamp>.<raw body>`, HMAC-SHA256 with the endpoint secret.
- Timestamp tolerance is 300 seconds; comparison is constant time.
- Every accepted event is inserted into `webhook_event` with a unique `(provider, external_event_id)`.
- The insert happens in its own transaction, so a concurrent duplicate loses the unique race and
  is answered with `duplicate=true`.
- Three event types change state. Everything else is stored and marked `IGNORED`.

Source: `payment-service/src/main/java/com/snehal/neopay/payment/webhook/`.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Trust an internal network boundary | The callback originates outside the cluster |
| Replay guard in Redis only | A cache loss would allow a second capture |
| Process without storing the event | Loses the audit trail and the replay capability |
| Return non-2xx on handler failure | Triggers processor retries for a request that cannot succeed |

## Consequences

- Duplicate delivery costs one insert and one read, never a second ledger posting.
- The event table grows without bound until a retention job exists.
- One secret per environment; rotation requires a deployment.
- A failed event is acknowledged and left for an operator, so failures need monitoring.
