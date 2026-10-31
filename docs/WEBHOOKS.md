# Inbound PSP Webhooks

## Why inbound, not only outbound

Merchant webhooks (`operations-service`) notify merchants after a state change. They do not
prove that NeoPay can consume a processor callback. Card and UPI rails are asynchronous:
the authorization result arrives as a signed HTTP POST, not as the response to the charge
call. `payment-service` therefore exposes its own inbound endpoint.

## Endpoint

| Property | Value |
|---|---|
| Method and path | `POST /webhook/stripe` |
| Authentication | `Stripe-Signature` HMAC; no merchant API key |
| Gateway | public route; routed to `payment-service` before the merchant `/webhook/**` route |
| Body | raw JSON, read as `String` so the signature covers the exact bytes |

## Signature

Header format: `t=<unix-seconds>,v1=<hex>[,v1=<hex>]`.

| Step | Rule |
|---|---|
| Signed payload | `<t>.<raw-body>` |
| Algorithm | HMAC-SHA256 with the endpoint secret (`stripe.sandbox.webhook-secret`) |
| Tolerance | 300 seconds of clock skew |
| Comparison | `MessageDigest.isEqual` per candidate, constant time |
| Secret absent | request rejected (`WEBHOOK_SECRET_MISSING`); fails closed |
| Any failure | HTTP 400, nothing persisted |

## Replay defence

`webhook_event` table, unique `(provider, external_event_id)`.

| Step | Behaviour |
|---|---|
| First delivery | row inserted with status `RECEIVED`, then processed |
| Duplicate delivery | HTTP 200, `duplicate=true`, no state change |
| Concurrent duplicate | insert loses the unique race, treated as duplicate |
| Processing error | row kept with status `FAILED` and the error text; HTTP 200 |

The 200 on failure is deliberate: a non-2xx would make the processor retry a request that
cannot succeed. Failed events stay queryable for replay by an operator.

## Event mapping

| Stripe event | Payment state | Side effects |
|---|---|---|
| `payment_intent.succeeded` (from `AUTHORIZING`) | `AUTHORIZED` → `CAPTURING` → `CAPTURED` | bank reference, `postCapture` ledger pair, order `PAID`, metric |
| `payment_intent.succeeded` (from `AUTHORIZED`) | `CAPTURING` → `CAPTURED` | same capture side effects |
| `payment_intent.succeeded` (terminal state) | unchanged | acknowledged, `IGNORED` |
| `payment_intent.payment_failed` | `AUTHORIZING` → `FAILED` | decline code and message recorded, failure metric |
| `payment_intent.canceled` | `AUTHORIZING` → `CANCELLED` | none |
| any other type | unchanged | acknowledged, `IGNORED` |

Payment resolution: `data.object.metadata.payment_id`, falling back to
`data.object.id` matched against `payment.processor_reference`.

## Outbound correlation

`StripeSandboxProcessor` sends `metadata[payment_id]=<payment id>` on the PaymentIntent, so
the callback can be matched without storing the processor id first. `processor_reference`
remains the fallback for events that predate the metadata convention.

## Ordering and idempotency

- Out-of-order delivery is safe: terminal states are not transitioned again.
- The ledger post is guarded twice, by the state machine and by the
  `(transaction_id, credit_account)` unique key.
- Two distinct events for the same payment cannot double-capture because capture requires
  `AUTHORIZING` or `AUTHORIZED`.

## Tests

| Test | Covers |
|---|---|
| `StripeWebhookVerifierTest` | valid, tampered, stale, multiple `v1`, malformed, missing secret |
| `StripePaymentEventApplierTest` | each event type, terminal-state no-op, resolution order, unknown payment |
| `StripeWebhookServiceTest` | processed, ignored, duplicate, insert race, failure, invalid JSON |
| `StripeWebhookControllerTest` | header binding, null header, response shape |

Contract tests for the outbound direction remain in
`StripeSandboxProcessorContractTest` (WireMock).

## Limitations

- Stripe only. The verifier is provider-specific; a second provider needs its own header
  scheme and its own event table namespace.
- No automatic replay endpoint. A `FAILED` row is replayed by re-posting the stored payload
  with a fresh signature, which an operator does deliberately.
- No webhook endpoint rotation. One secret per environment.
