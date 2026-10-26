# Design Notes

## Goal

Move money correctly under retry, concurrency and partial failure, and be able to prove
it.

## Invariants

| Invariant | Where enforced | Test |
|---|---|---|
| `fee + gst + net == gross` | `SettlementFeeCalculator` | unit + property, 30k cases |
| Fee never exceeds gross | `FeeSchedule` | property |
| Capture never exceeds authorization | `PaymentAggregate` | unit |
| Cumulative refund never exceeds capture | `PaymentAggregate` | unit |
| `SETTLED` unreachable without `CAPTURED` | `PaymentStateMachine` | 11×11 matrix, property |
| `CAPTURED` unreachable without `AUTHORIZED` | `PaymentStateMachine` | 11×11 matrix, property |
| One payment per `(merchant, idempotency key)` | database unique index | 50-thread test |
| Money arithmetic never overflows | `Math.*Exact` | unit |
| PAN never appears in logs | log scrubbing | `PanLoggingTest` |

## Consistency model

| Interaction | Guarantee |
|---|---|
| Payment write + outbox write | Atomic, one database transaction |
| Outbox → Kafka | At-least-once |
| Kafka → webhook delivery | At-least-once with bounded backoff and DLQ |
| Gateway call → payment state | Eventually consistent, with compensation |
| Idempotent replay | Effectively-once from the client's view |
| Settlement → payment `SETTLED` | Eventually consistent, guarded by the state machine |

No component claims exactly-once delivery. The system claims **effectively-once
processing**, which is at-least-once transport plus idempotent handling.

## Failure modes designed for

| Failure | Behaviour |
|---|---|
| Gateway timeout during authorization | Compensation writes `FAILED` and `PAYMENT_AUTHORIZATION_COMPENSATED` |
| Duplicate concurrent request | Redis marker rejects early; unique index is the authority |
| Poller crash mid-publish | Row stays `PENDING` or `FAILED`; replay publishes again; consumers are idempotent |
| Webhook endpoint down | Backoff `1m, 5m, 30m, 2h, 8h, 24h`, then `DEAD` plus DLQ row |
| Redis loss | Idempotency, rate limiting and cache fail open; database constraints still hold |
| Kafka consumer failure | Manual ack prevents silent loss; retries are bounded |
| Duplicate status transition | State machine rejects; `409 INVALID_STATE_TRANSITION` |

## Deliberate non-goals

- Real card network integration (ISO 8583) or bank settlement files (ISO 20022).
- Multi-currency conversion. `Currency` models scale; FX is absent.
- Disputes, chargebacks, payouts, KYC lifecycle.
- Multi-region deployment and disaster recovery.
- Exactly-once messaging.

These are stated so the absence is a decision, not an oversight.

## Rejected alternatives

See `docs/adr/`. The recurring pattern: choose the mechanism with the smallest
operational surface that still satisfies the invariant, and make the failure mode
explicit.

## Known weak points

| Weak point | Consequence | Next step |
|---|---|---|
| Entity setters remain | Invariants can be bypassed outside the aggregate | Make the aggregate the write path |
| No event store | No historical replay or audit trail beyond transition logs | Staged event sourcing (ADR 0008) |
| Line coverage 24.8% | Regressions in untested paths are possible | Raise coverage on service packages |
| Refund API absent | `PARTIALLY_REFUNDED` is reachable only through the aggregate | Implement refund endpoints |
| Settlement uses a global fee rate | Per-merchant pricing unsupported | `FeeSchedule` per merchant |
| Trace propagation over Kafka untested | Cross-service correlation may break at the broker | Add header propagation test |
