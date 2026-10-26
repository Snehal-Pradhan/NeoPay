# Deterministic Payment Simulation — 10 Million Transactions

## Problem

Payment systems fail in schedules, not in single requests. A retry that duplicates a
charge, a refund that races a settlement, a callback that arrives after a timeout —
each is rare alone and expensive in combination. Example-based tests cover the cases
someone thought of. They cannot cover the schedules nobody wrote down.

This document reports a deterministic simulator that executes the payment lifecycle
under seeded fault schedules and asserts money invariants on every iteration.

## Model

One iteration represents one logical transaction:

1. A client submits a request with an idempotency key.
2. With 10% probability the key repeats the previous request.
3. With 5% probability authorization fails.
4. Otherwise the payment is captured: `CUSTOMER_FUNDS → MERCHANT_PAYABLE`.
5. The full amount is settled: fee to `PLATFORM_FEE`, GST to `GST_PAYABLE`, net to
   `BANK_SETTLEMENT`.
6. With 10% probability a refund occurs, partial or full:
   `BANK_SETTLEMENT → CUSTOMER_FUNDS`.

Amounts are uniform in `[1, 1_000_000]` minor units.

Every movement is a double-entry posting. A posting names a debit account, a credit
account and an amount.

### Production code, not a copy

| Rule | Source |
|---|---|
| Fee, GST and net arithmetic | `com.snehal.neopay.common.money.FeeSchedule` |
| Authorize, capture, settle, refund transitions | `PaymentStateMachine` |

The simulator does not reimplement the fee schedule or the state machine. It calls
them. A divergence in either fails the run.

## Invariants

| Invariant | Check |
|---|---|
| Money is conserved | total debits == total credits after every iteration |
| No residual liability | `MERCHANT_PAYABLE` balance is zero after settlement |
| Refund bounded | refund never exceeds captured amount |
| Settlement exact | `fee + gst + net == gross`, checked through `Money.equals` |
| Duplicate safety | a repeated idempotency key never creates a second transaction |
| Lifecycle legal | every step is accepted by `PaymentStateMachine` |

A violation is recorded with the transaction index and the run stops after twenty.

## Determinism

The simulator uses a single `java.util.Random` seeded by the run argument. No wall
clock, no threads, no external services. Same seed and same count produce an identical
`SimulationResult`, asserted by `sameSeedProducesIdenticalResults`.

## Results

Two seeds, ten million transactions each, JDK 21, single thread, in-memory.

| Seed | Transactions | Elapsed | Captured | Auth failures | Duplicates | Refunds | Settled | Violations |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 42 | 10,000,000 | 2,426 ms | 8,549,463 | 449,935 | 1,000,602 | 854,026 | 8,549,463 | **0** |
| 2026 | 10,000,000 | 2,254 ms | 8,549,519 | 451,348 | 999,133 | 855,321 | 8,549,519 | **0** |

Ledger totals for seed 42: 8,762,889,465,345 debits and the same credits.
Final `MERCHANT_PAYABLE` balance: 0.

## Reproduction

```bash
mvn -q -f common-lib/pom.xml -DskipTests install
mvn -q -f payment-service/pom.xml -DskipTests compile

java -cp payment-service/target/classes:common-lib/target/classes \
  com.snehal.neopay.payment.simulation.SimulationMain 42 10000000
```

Output:

```
seed=42 transactions=10000000 elapsedMs=2426
captured=8549463 failedAuth=449935 duplicates=1000602 refunds=854026 settled=8549463
debits=8762889465345 credits=8762889465345 finalMerchantPayable=0
violations=0
```

The process exits non-zero when any violation is recorded.

## What this proves

- The production fee schedule conserves value across ten million random amounts.
- The production state machine accepts every simulated lifecycle and rejects nothing.
- Duplicate idempotency keys never double-count.
- Partial refunds never exceed capture.
- The ledger returns to a zero merchant liability after every transaction.
- Failures are reproducible from a seed.

## What this does not prove

- **No concurrency.** Iterations are sequential. The database-level idempotency
  guarantee is tested separately by a fifty-thread test against the real unique index.
- **No network partitions or clock skew.** Faults are limited to duplicate requests and
  authorization failure.
- **The ledger is a model.** Postings are asserted in memory; they do not execute
  against Postgres. The production ledger is not yet event-sourced end to end.
- **Fee refunds are full-value.** A refund claws back the gross amount from the
  settlement account; fee and GST are not reversed. Real schedules may refund fees
  proportionally.

## Next step

Drive the simulator through `PaymentServiceImpl` against an in-memory or containerized
database, so persistence and transaction boundaries are exercised as well as the rules.

## External PSP boundary

The simulator is the default processor. To prove the boundary is not simulator-shaped,
`payment-service` also contains `StripeSandboxProcessor`: it calls the PaymentIntents
API shape (`POST /v1/payment_intents`), sends the payment id as `Idempotency-Key`, and
maps `succeeded`, `requires_action`, card declines (`decline_code`) and idempotency
conflicts to the internal `PaymentProcessorResponse` types. `StripeSandboxProcessorContractTest`
runs those five contracts against WireMock, so the mapping is verified without a live
Stripe account or any money movement.

The adapter is not in the default routing table. Enabling it is a configuration change
(base URL and test key) plus a router entry, not a code change in the domain layer.
