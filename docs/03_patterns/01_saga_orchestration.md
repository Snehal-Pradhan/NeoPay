# Saga orchestration

A payment spans a local order row, a processor call, and a ledger post. NeoPay keeps the saga inside one service and compensates on failure. No distributed transaction coordinator exists.

## Steps

| Step | Action | Compensating action |
|---:|---|---|
| 1 | Lock order row, validate status and expiry | None needed |
| 2 | Enforce risk limits | None needed |
| 3 | Insert payment in `CREATED`, transition to `AUTHORIZING` | Mark `FAILED` |
| 4 | Call the processor boundary | `compensateAuthorizationFailure` |
| 5 | On success, authorize and capture | Capture failure returns the payment to `AUTHORIZED` |
| 6 | Post one ledger pair | None; the post is inside the same transaction as the capture |
| 7 | Publish outbox event | Event row is part of the same transaction |

Source: `payment-service/src/main/java/com/snehal/neopay/payment/saga/PaymentAuthorizationRecorder.java`.

## Compensation rules

| Failure point | Payment state after compensation | Ledger |
|---|---|---|
| Router unreachable before the call | `FAILED` with `PAYMENT_GATEWAY_ROUTER_UNREACHABLE` | No posting |
| Processor returns failure | `FAILED` with the processor code | No posting |
| Capture fails after authorization | `AUTHORIZED` with the failure code | No posting |
| Processor result arrives asynchronously and is declined | `FAILED` on the webhook path | No posting |

The order is saved with the payment in every branch, so order status and payment status never diverge inside one transaction boundary.

## Why orchestration, not choreography

| Option | Trade-off |
|---|---|
| Orchestration in one service (used) | One place to read the flow; the service is a deployment bottleneck for that flow |
| Choreography over Kafka | More decoupled; harder to reason about a half-finished payment |
| Two-phase commit | Not available across the processor boundary |

## Boundaries and limits

- The saga does not span services. Settlement is a separate workflow with its own failure handling.
- There is no saga state table. The payment row and its transition log are the state.
- A crash between the processor call and the local commit leaves `AUTHORIZING`; the simulator or the webhook handler resolves it.
- Compensation is not retried automatically. The webhook path or an operator resolves the remaining state.

## Evidence

| Test | Asserts |
|---|---|
| `PaymentAuthorizationRecorderTest` | order validation, compensation, ledger side effects |
| `PaymentAuthorizationRecorderGatewayTest` | pending, failure and compensation branches |
| `PaymentStateMachinePropertyTest` | no capture without authorization, no settle without capture |

Related: [payment service](../02_services/payment_service.md), [outbox](02_transactional_outbox.md), [resilience](04_resilience_circuit_breakers.md).
