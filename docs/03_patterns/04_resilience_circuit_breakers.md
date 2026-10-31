# Resilience, retries, and limits

Each remote dependency has an explicit failure policy. The policy favours availability of the payment path over strictness of auxiliary checks.

## Policies

| Dependency | Pattern | Configuration | On failure |
|---|---|---|---|
| merchant-service (risk) | Circuit breaker plus retry | window 10, 50% threshold, open 10 s, 3 half-open calls, 5 attempts 1 s apart | Fail open; payment proceeds, counter incremented |
| vault-service | Circuit breaker plus retry | window 10, 50% threshold, open 15 s, 3 half-open calls, 5 attempts 1 s apart | Card charge fails; other methods proceed |
| Processor | Timeout plus compensation | connect 2 s, read 5 s | Payment marked failed with a code |
| Postgres | Transaction | Default isolation | Request fails; no partial write |
| Kafka | Outbox | Poll interval, 3 attempts | Event stays queued or becomes `FAILED` |
| Merchant webhook | Retry queue | Exponential attempts, dead-letter after exhaustion | Delivery stops; operator requeues |

Configuration: `config-service/src/main/resources/config/payment-service.yml`.

## Fail-open versus fail-closed

| Check | Policy | Reason |
|---|---|---|
| Risk limits | Fail open | A merchant risk outage would stop all payments |
| Card data | Fail closed | No charge without a token |
| Settlement bank details | Fail closed | No transfer without an account |
| Idempotency | Fail open at the gateway, closed at the database | Correctness rests on the unique index |
| Rate limiting | Fail open | Availability over throttling during a cache outage |

Every fail-open decision increments a counter, so the condition is observable rather than silent.

## Rate limiting

| Property | Value |
|---|---|
| Algorithm | Fixed 60-second window |
| Scope | Per API key |
| Limit | 200 requests per minute |
| Storage | Redis counter |
| Observed effect | At 60 keys and 60 flows/s, bursts produced 0.11% `429` responses |

A fixed window allows up to roughly twice the configured rate across a boundary. A sliding window or token bucket removes that burst. Measurement and reproduce steps: [PERFORMANCE](../PERFORMANCE.md).

## Bulkheads

| Boundary | Isolation |
|---|---|
| Connection pools | One Hikari pool per service |
| Scheduler locks | One ShedLock lease per job name |
| Kafka consumers | Separate listener per topic set |
| Webhook delivery | Bounded queue per merchant target |

## What is not implemented

| Capability | Status |
|---|---|
| Timeouts on every client | Partial; configured for merchant and vault |
| Retry budgets across the call graph | None |
| Bulkhead limits on thread pools | None |
| Load shedding under saturation | None |
| Chaos in CI | Manual scripts only |

Related: [saga](01_saga_orchestration.md), [outbox](02_transactional_outbox.md), [runbook](../runbook.md).
