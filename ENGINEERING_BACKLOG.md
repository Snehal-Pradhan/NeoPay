# Engineering Backlog — Correctness, Fintech, Rigor, Performance

This is the non-DevOps half of the upgrade. It is ordered by **points per hour**, not by how interesting it is.

Legend: `[ ]` todo · `[x]` done · `[~]` optional

---

## Why this file exists

The project currently scores **8/10 on architecture** and **2/10 on verification**. That mismatch is the whole problem. Everything below converts architectural intent into provable behavior.

A payment platform is judged on three things:

1. **Does the money add up?** (ledger, rounding, reconciliation)
2. **Does it stay correct when things fail?** (idempotency, compensation, retries)
3. **Can you prove it?** (tests with assertions, invariants, numbers)

---

## P0 — Real defects found in the current code

- [ ] `operations-service/.../SettlementTransactionExecutor.java` uses `double FEE_RATE = 0.02` and `Math.round(gross * FEE_RATE)`.
      - Money must never touch `double`. Replace with integer basis points: `fee = amount * 200 / 10_000` using `Math.multiplyExact`/`BigDecimal` with explicit `RoundingMode.HALF_UP`.
      - GST is currently computed on the rounded fee, which is correct practice — preserve that ordering.
      - Add a test at the boundary: amount 1 paisa, amount 2 paise, amount `Integer.MAX_VALUE / 2`.
- [ ] `Money` is `int amountUnits` with no scale, no currency validation, no overflow guard, and no `multiply`/`percentage`.
      - `add`/`subtract` can silently overflow. Use `Math.addExact` / `Math.subtractExact`.
      - Only `INR` is truly supported; reject unknown currencies explicitly.
      - `int` caps at ~₹21.47 crore — document the ceiling, or move to `long`.
- [ ] No centralized payment/order transition table. Illegal transitions are not provably rejected.
      - Add `PaymentStateMachine.canTransition(from, to)` driven by an enum map, reject with `INVALID_STATE_TRANSITION` (409).
      - Every legal edge gets a test; every illegal pair gets a rejection test.

**Done when:** `grep -rn "double" --include='*.java'` finds no money arithmetic, and the state machine has an exhaustive transition test.

---

## P1 — Fintech domain (highest differentiation)

### 1. Immutable double-entry ledger

Currently the system tracks payment **status** but has no accounting record. This is the single biggest fintech gap.

- [ ] `ledger_entry` table: `id`, `transaction_id`, `account` (`MERCHANT_RECEIVABLE`, `PLATFORM_FEE`, `GST_PAYABLE`, `MERCHANT_PAYABLE`), `debit`, `credit`, `currency`, `created_at`, `reference_type`, `reference_id`
- [ ] Append-only: no updates, no deletes, enforced by trigger or repository design
- [ ] Every payment, capture, refund, fee, and settlement writes entries **in the same transaction** as the business write
- [ ] Invariant: `sum(debit) == sum(credit)` per `transaction_id` — assert in code and in a nightly checker
- [ ] `LedgerInvariantChecker` scheduled job that scans for imbalances and alerts
- [ ] Money movement is derived from the ledger, not from mutable status fields

**Done when:** the sum of all debits equals the sum of all credits across the whole history, and a test proves an unbalanced write is rejected.

**Why it scores:** this is the first thing a payments interviewer probes. Nobody expects a tutorial clone to have a ledger.

### 2. Reconciliation against an external statement

- [ ] `POST /internal/recon/ingest` accepting a simulated bank/PSP settlement CSV
- [ ] Match on `bank_reference` + amount + date
- [ ] Classify breaks: `MISSING_INTERNAL`, `MISSING_EXTERNAL`, `AMOUNT_MISMATCH`, `DUPLICATE`, `CURRENCY_MISMATCH`
- [ ] `recon_run` + `recon_break` tables; a daily job plus an on-demand trigger
- [ ] Metric: `recon_breaks_total{type=...}`; alert on any non-zero
- [ ] A deliberately corrupted statement fixture that produces exactly 4 known breaks

**Done when:** you can show a recon report with a real break and the runbook step that resolves it.

**Why it scores:** reconciliation is the difference between "payment demo" and "payment system." Very few candidates have touched it.

### 3. Refunds — actually implement them

Currently entity/enums only. This is the largest missing domain feature.

- [ ] `POST /v1/payments/{id}/refunds` with `X-Idempotency-Key`
- [ ] Partial and multiple refunds; reject `sum(refunds) > captured`
- [ ] Refund state machine: `CREATED → PENDING → PROCESSED | FAILED`
- [ ] Ledger reversal entries (not status flips)
- [ ] Payment transitions to `PARTIALLY_REFUNDED` / `REFUNDED`
- [ ] Refund event on `refund.events` (the topic and consumer already exist and are idle)
- [ ] Webhook for `REFUND_PROCESSED`
- [ ] Refunds reduce the next settlement (negative carry-forward)

**Done when:** a captured payment is refunded twice, partially, and the ledger balances with zero orphaned rows.

### 4. Settlement correctness

- [ ] Carry-forward balance per merchant for negative/insufficient settlement
- [ ] Hold period before funds become settleable (T+1 / T+2 configurable)
- [ ] Fee schedule per merchant, not a global constant
- [ ] Rounding rule documented and tested at paisa boundaries
- [ ] Settlement statement artifact (JSON/CSV) a merchant could actually read

### 5. Inbound webhook verification

Outbound signing exists. Inbound does not.

- [ ] `POST /webhook/inbound` verifying `X-Signature` HMAC over the raw body
- [ ] Timestamp tolerance (reject > 5 minutes skew)
- [ ] Replay cache in Redis keyed on signature + timestamp
- [ ] Constant-time comparison (`MessageDigest.isEqual`)
- [ ] Tests: valid, tampered body, stale timestamp, replayed request

### 6. PCI-DSS scope reduction, documented

- [ ] Test asserting PAN never appears in logs: capture log output during a tokenize + charge flow, assert `"4111111111111111"` is absent
- [ ] Test asserting PAN never crosses the `payment-service` boundary
- [ ] `docs/pci-scope.md`: what is in scope, what is not, and why tokenization moves the boundary
- [ ] Masking in every DTO `toString()` (Lombok `@ToString.Exclude` on card fields)

**Why it scores:** it proves you think about blast radius, not just features. Very cheap, very high signal.

### 7. Time discipline

- [ ] Store UTC everywhere (`Instant`), never `LocalDateTime`
- [ ] Settlement "23:00" pinned to an explicit `ZoneId` from config (IST for India), documented
- [ ] Test that a settlement run across a DST boundary in another zone still fires once per day

---

## P2 — Engineering rigor (cheap, high signal)

### 8. Property-based tests (jqwik)

- [ ] State machine: no sequence of transitions can reach `SETTLED` without passing `CAPTURED`
- [ ] `Money`: `a.add(b).subtract(b) == a` for all non-overflowing values
- [ ] Idempotency: for any request replayed N times, exactly one aggregate is created
- [ ] Fee math: `fee + net + gst == gross` for 100,000 random amounts

**Why it scores:** almost no candidate does this. It reads as senior immediately.

### 9. Mutation testing (PIT)

- [ ] Run PIT on the payment and settlement packages
- [ ] Get mutation score above 60% (this forces real assertions)
- [ ] Publish the score in the README

### 10. Contract tests

- [ ] Spring Cloud Contract or Pact between gateway ↔ merchant and payment ↔ vault
- [ ] Include the polymorphic `VaultChargeResponse` shape as a contract — this is the bug that already bit you
- [ ] Run contracts in CI

**Why it scores:** you have a real cross-service serialization failure in your history. A contract test is the exact engineering response, and the story writes itself.

### 11. ADRs (Architecture Decision Records)

Six to eight, one page each, in `docs/adr/`:

- [ ] `0001-outbox-over-dual-write.md`
- [ ] `0002-shedlock-over-db-advisory-lock.md`
- [ ] `0003-db-idempotency-plus-redis.md`
- [ ] `0004-saga-compensation-over-distributed-transaction.md`
- [ ] `0005-per-service-database.md`
- [ ] `0006-tokenization-for-pci-scope.md`
- [ ] `0007-native-config-over-git-config-server.md`
- [ ] `0008-why-not-microservices-yet.md` ← the honest one

Each: context → decision → alternatives rejected → consequences.

**Why it scores:** judgment is the scarcest signal, and this is the cheapest way to display it. ~30 minutes each.

### 12. OpenAPI

- [ ] `springdoc-openapi` on all services
- [ ] Commit the generated `openapi.yaml`
- [ ] CI check that the spec changed intentionally (diff gate)
- [ ] Generated TypeScript or Java client for one consumer

---

## P3 — Performance and JVM depth

### 13. Find one real bottleneck with evidence

- [ ] Async-profiler or JFR on `POST /v1/payments` under load
- [ ] Produce a flame graph, identify the hot path (suspect: BCrypt verification, JPA flush, or Redis round-trip per request)
- [ ] Fix it, re-measure, report before/after
- [ ] Add the regression guard (cache, index, or pool size)

**Done when:** you have "p95 240ms → 150ms, cause: N+1 on order lookup, fix: fetch join + index" with evidence.

**Why it scores:** a measured improvement is the only performance claim worth making.

### 14. JVM and container tuning

- [ ] Generational ZGC (`-XX:+UseZGC -XX:+ZGenerational`), documented
- [ ] `-XX:MaxRAMPercentage` instead of fixed `-Xmx`
- [ ] Heap/GC panel on the Grafana dashboard
- [ ] One paragraph on pause-time tradeoffs for a latency-sensitive service

### 15. Backpressure and load shedding

- [ ] Resilience4j Bulkhead on vault and merchant calls (partially present)
- [ ] Concurrency limit on payment initiation
- [ ] Return `503` + `Retry-After` instead of queueing unboundedly
- [ ] Test: 2× capacity load, assert graceful degradation and zero 500s

---

## P4 — Interview story bank

Write these down in `docs/stories/` — one page each, STAR format. These are worth more than another endpoint.

- [ ] The polymorphic Feign deserialization failure (sealed interface across the wire)
- [ ] Kafka `ack-mode: manual` exhaustion (messages silently retried to death)
- [ ] Topic key singular/plural mismatch (producer and consumer never met)
- [ ] MapStruct silently dropping `status` (null in the API, no error anywhere)
- [ ] Postgres `max_connections` exhaustion during local runs
- [ ] Compensation leaving the system consistent after a gateway failure

Each story needs: symptom → how you found it → root cause → fix → what you changed to prevent recurrence.

---

## Cut list

- [~] Wallet payments — no adapter, no processor, low signal
- [~] GraphQL or gRPC "for novelty"
- [~] A rewrite in another language
- [~] More services for the count
- [~] Blockchain, AI, or anything that does not make the money add up

---

## Recommended order

```
P0 defects  →  ledger  →  refunds  →  property tests  →  recon  →  ADRs  →  profiling  →  contract tests
```

## Related

- `ROADMAP.md` — **start here**: unified order, schedule, and scoring across DevOps and engineering
- `DEVOPS_CHECKLIST.md` — CI, infrastructure, observability, supply chain

P0 first because a `double` in fee math is a genuine bug an interviewer can find. Ledger and refunds next because they are the domain differentiators. Everything after is polish that compounds.
