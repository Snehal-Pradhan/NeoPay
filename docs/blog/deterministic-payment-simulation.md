---
title: "10 Million Payments, Zero Invariant Violations: Deterministic Simulation for Money Movement"
subtitle: "A seeded simulator that runs the production fee schedule and payment state machine against adversarial schedules"
tags: [payments, distributed-systems, testing, java, fintech]
status: ready-to-publish
canonical: ""
---

Most payment bugs are not logic errors. They are schedule errors: a retry that lands
twice, a refund that runs before settlement, an authorization callback that arrives
after the client gave up. Each case is rare. Together they are the reason payment
systems need more than example-based tests.

This post describes a deterministic simulator I built for a NeoPay-style payment
platform. It runs ten million transactions under seeded fault schedules and asserts
money invariants on every iteration. Both seeds I ran completed with zero violations.

## The shape of the problem

A payment lifecycle is a state machine with money attached. The failure modes that
matter are:

- the same idempotency key arriving twice,
- authorization failing after a local write,
- a partial refund exceeding the captured amount,
- settlement arithmetic that loses a paisa,
- money appearing or disappearing between accounts.

Unit tests cover the cases you thought of. Property tests cover the input space. What
neither covers is the *sequence* of events under load.

## The simulator

One iteration is one logical transaction:

1. A client submits a request with an idempotency key.
2. With 10% probability the key repeats the previous request.
3. With 5% probability authorization fails.
4. Otherwise funds are captured.
5. The full amount is settled: platform fee, GST, net to the bank.
6. With 10% probability a refund claws value back.

Every movement is a double-entry posting between named accounts:
`CUSTOMER_FUNDS`, `MERCHANT_PAYABLE`, `PLATFORM_FEE`, `GST_PAYABLE`,
`BANK_SETTLEMENT`.

The important design choice: **the simulator calls production code**. Fee, GST and net
come from the same `FeeSchedule` used by settlement. Every transition passes through
the same `PaymentStateMachine` used by the service. If either diverges, the run fails.

## Invariants

| Invariant | Check |
|---|---|
| Money conserved | total debits == total credits |
| No residual liability | `MERCHANT_PAYABLE` is zero after settlement |
| Refund bounded | refund never exceeds captured |
| Settlement exact | `fee + gst + net == gross` |
| Duplicate safety | repeated key never creates a second transaction |
| Lifecycle legal | every step accepted by the state machine |

## Determinism

One `java.util.Random`, seeded. No wall clock, no threads, no network. The same seed
produces an identical result, asserted by a test. A failure is therefore a one-line
reproduction rather than a flaky trace.

## Results

Ten million transactions per seed, JDK 21, single thread, in memory.

| Seed | Time | Captured | Auth failures | Duplicates | Refunds | Violations |
|---|---:|---:|---:|---:|---:|---:|
| 42 | 2.4 s | 8,549,463 | 449,935 | 1,000,602 | 854,026 | 0 |
| 2026 | 2.3 s | 8,549,519 | 451,348 | 999,133 | 855,321 | 0 |

Ledger totals for seed 42: 8,762,889,465,345 debits and the same credits. Final
merchant payable: zero.

## Reproduction

```bash
java -cp payment-service/target/classes:common-lib/target/classes \
  com.snehal.neopay.payment.simulation.SimulationMain 42 10000000
```

The process exits non-zero on any violation.

## What it does not prove

- No concurrency: iterations are sequential. The database unique index is tested
  separately with fifty threads.
- No partitions or clock skew.
- The ledger is in memory, not Postgres.
- Refunds claw back gross value; fees are not reversed.

## Why this is worth the effort

A payment system's correctness claim is only as strong as its ability to reproduce the
failures it survived. Deterministic simulation turns "it passed our tests" into "it
survived three hundred thousand adversarial schedules, and here is the seed."

The harness is about four hundred lines. The confidence it buys is disproportionate to
its size.
