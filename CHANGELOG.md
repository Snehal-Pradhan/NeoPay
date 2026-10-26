# Changelog

All notable changes to the microservices edition are documented here.

## [Fintech Gate 12] — 2026-10-26 — formal model

### Added

- TLA+ specification of the payment lifecycle and ledger with six invariants.
- TLC configuration; recorded run: 9 reachable states, depth 7, no violations.
- `ModelConformanceTest`: every model edge is accepted by the production state machine
  with the same target state.
- `docs/FORMAL.md`: model description, results, reproduction, limits.
- Publication-ready article remains under `docs/blog/`.

### Verified

- TLC completes with no error and no state left on the queue.
- 8 conformance tests pass.
- 399 tests across eight modules, 0 failures.

### Not done

- External publication; the article requires an account.

## [Fintech Gate 8] — 2026-10-26 — FX and multi-currency

### Added

- `Money` accepts every currency in the `Currency` enum.
- `FxConverter`: rate plus spread in basis points, HALF_UP at target scale, identity for
  same-currency conversion.
- `FxRateProvider`: configured rates behind a provider seam.
- `FxRateLock` and `FxRateLockService`: lock at payment initiation with a TTL; expired
  locks are rejected.
- Non-INR payments lock a rate when recorded; settlement views convert captured amounts
  to INR using the lock.

### Verified

- USD to INR at 83.00 converts 1.00 USD to 83.00 INR; spread applied; round trip drift
  within one minor unit.
- Expired and missing locks rejected.
- 6 converter tests, 4 lock tests, lookup conversion test.
- 391 tests across eight modules, 0 failures.

### Limits

- Refunds are not converted.
- Configured rates use zero spread by default.
- Only INR quotes are supported.

## [Fintech Gate 9] — 2026-10-26 — ISO 20022

### Added

- Minimal XSD profiles for `pacs.008`, `camt.053` and `pain.001` bundled in common-lib.
- `Iso20022Codec`: parse and generate all three message types, with XSD validation
  before parsing and DTD or external-entity expansion disabled.
- `camt.053` output maps directly to reconciliation rows.
- Endpoints under `/internal/iso20022` for parse, build and statement ingestion.
- Amount handling converts ISO decimal major units to internal minor units at currency
  scale, rejecting excess precision.

### Verified

- Round trip for all three messages.
- Malformed document, DOCTYPE payload, unknown currency and excess precision rejected.
- `camt.053` feeds `StatementRow` for reconciliation.
- 379 tests across eight modules, 0 failures.

## [Fintech Gate 10 completion] — 2026-10-26

### Added

- `RiskClient` in payment-service and `RiskEnforcer` applied before a payment is
  recorded.
- Rejected decisions return `403` with the risk reason; step-up returns
  `428 STEP_UP_REQUIRED`; an unreachable merchant service fails open and counts
  `risk_unavailable_total`.
- `risk.enforcement-enabled` flag, off locally and on in production.

### Fixed

- Duplicate Feign specification name when two clients target `merchant-service`;
  `contextId` added to both.

### Verified

- 5 enforcer tests; full payment suite passes.
- Gate 10 at 100%.

## [Fintech Gate 10] — 2026-10-26 — KYC, limits and risk

### Added

- `KycService`: verify activates a merchant, reject suspends it, tier upgrade persisted.
- `MerchantTier` with per-transaction, daily and velocity limits; stored on the merchant.
- `MerchantLimitUsage`: daily amount and request counters.
- `SanctionsScreener` interface with a configured-list implementation behind it.
- `RiskService`: KYC check, sanctions check, per-transaction limit, daily limit, velocity
  step-up. Returns specific reasons: `KYC_NOT_VERIFIED`, `SANCTIONS_HIT`,
  `LIMIT_EXCEEDED`, `DAILY_LIMIT_EXCEEDED`, `STEP_UP_REQUIRED`, `INVALID_AMOUNT`.
- Internal endpoints for authorization, KYC verification, rejection and tier change.

### Verified

- 13 tests across risk and KYC.
- 364 tests across eight modules, 0 failures.

### Not done

- Payment initiation does not call the risk endpoint yet; enforcement is not wired into
  the transaction path.
- Sanctions screening is a configured substring list, not a provider integration.

## [Fintech Gate 11] — 2026-10-26 — reporting

### Added

- `ReportService`: settlement report derived from ledger lines with reference type
  `SETTLEMENT`, and a tax report over `GST_PAYABLE` and `TDS_PAYABLE`.
- Deterministic rendering: rows sorted by account name, no timestamps, identical bytes
  on repeat runs.
- `GET /internal/reports/settlement`, `GET /internal/reports/tax`.
- `ReconAttestationService`: canonical summary with SHA-256 digest over sorted breaks
  and an HMAC signature; `GET /internal/recon/runs/{id}/attestation`.
- `docs/REPORTING.md`: report list, determinism rules, attestation format and a
  retention policy table.

### Verified

- Settlement report excludes payment postings; rows sort by account name.
- Same date produces byte-identical output.
- Attestation is identical across calls and includes digest and signature.
- 351 tests across eight modules, 0 failures.

### Not done

- Retention policy is documented, not enforced by a purge job.
- No period close or trial balance.
- Reports are on demand; no signed daily drop to object storage.

## [Fintech Gates 5, 6, 7] — 2026-10-26

### Gate 5 — reserve release

- `ReserveReleaseService`: releases retained reserve to the settlement account after
  `reserve.hold-days`, once per reserve line, guarded by a unique source line id and
  ShedLock.

### Gate 6 — disputes

- `Dispute` lifecycle `OPENED → WON / LOST` with a source account captured at open.
- Opening a dispute posts `source -> DISPUTE_HOLD`; a won dispute reverses it; a lost
  dispute moves the hold to platform receivable.
- Endpoints under `/v1/payments/{paymentId}/disputes`, plus internal resolution.
- `DISPUTE_OPENED` and `DISPUTE_RESOLVED` events.

### Gate 7 — payouts

- `Payout` per settlement with idempotent creation, `NACH-` reference and CSV file.
- Return handling posts `BANK_SETTLEMENT -> MERCHANT_PAYABLE` and reopens the merchant
  carry-forward balance.
- `GET /internal/settlements/{id}/payout-file`,
  `POST /internal/payouts/{id}/return`.

### Verified

- Reserve release posts once per line.
- Dispute won/lost paths post the expected account pairs; duplicate resolution rejected.
- Payout creation is idempotent; return reopens carry-forward.
- 346 tests across eight modules, 0 failures.

## [Fintech Gate 5] — 2026-10-26 — settlement correctness

### Added

- TDS charged on the fee and a rolling reserve charged on the settlement base, both
  posted to dedicated ledger accounts.
- `MerchantFeeSchedule` with `FeeScheduleResolver`: per-merchant rates with the standard
  India schedule as fallback.
- `CarryForwardService`: a shortfall in one cycle is deducted from the next; a payment
  above the captured amount creates or grows the outstanding balance instead of a
  transfer.
- Hold period: payments captured inside `settlement.hold-days` are excluded.
- `GET /internal/settlements/{id}/statement` returns a CSV of every component.
- Ledger settlement request now carries gross, fee, GST, TDS, reserve and net and
  validates that the five components sum to gross.

### Verified

- 10,000 gross with 1,000 prior debt yields fee 200, GST 36, TDS 20, reserve 450,
  net 8,294 and clears the debt.
- Debt above captured produces a zero-net settlement and no transfer.
- Payments inside the hold window are skipped.
- Merchant schedule overrides the default; missing schedule falls back.
- 333 tests across eight modules, 0 failures.

### Not done

- Reserve release after the hold period.
- Refund amounts in the settlement view remain zero; refunds are posted to the ledger
  but not deducted from the settlement base.

## [Fintech Gate 4] — 2026-10-26 — three-way reconciliation

### Added

- `recon_run` and `recon_break` tables; breaks are append-only until resolved.
- `CsvStatementParser` for `reference,amountUnits,currency` files with header
  detection, blank-line handling and field validation.
- `ReconService`: matches internal settlements, PSP statement and bank statement on
  reference; classifies `MISSING_INTERNAL`, `MISSING_PSP`, `MISSING_BANK`,
  `AMOUNT_MISMATCH_PSP`, `AMOUNT_MISMATCH_BANK`, `CURRENCY_MISMATCH`, `DUPLICATE`.
- `POST /internal/recon/runs`, `GET /runs/{id}/breaks`,
  `POST /breaks/{id}/resolve`.
- Metric `recon_breaks_total{type}`.

### Verified

- Fixture with four seeded defects yields exactly four breaks of the expected types.
- Clean fixture yields zero breaks.
- Duplicates on either side are detected before missing/mismatch checks.
- 326 tests across eight modules, 0 failures.

## [Fintech Gate 2] — 2026-10-26 — idempotency coverage

### Changed

- Capture is idempotent by state: a payment already in `CAPTURED` returns the stored
  response without a gateway call, transition or ledger posting.
- Concurrent refunds with the same key are rejected with `409 IDEMPOTENCY_CONFLICT`
  instead of surfacing a database error. The unique index remains the authority.
- Refunds use `saveAndFlush` so the duplicate-key race is detected inside the request.

### Added

- `RefundConcurrencyTest`: 50 threads, same refund key, exactly one refund row.
- Capture replay test: no gateway call, no duplicate ledger posting.

### Verified

- 316 tests across eight modules, 0 failures.
- Payments, capture, refunds and settlement ledger postings all have replay or
  duplicate-concurrency coverage.

## [Fintech Gate 3] — 2026-10-26 — refunds

### Added

- `Refund` with behaviour methods, unique `(payment_id, idempotency_key)` and a
  reason field. Setters removed.
- `RefundService`: partial and multiple refunds, full-refund transition, over-refund
  rejection, currency check, refundable-status check, replay.
- `LedgerService.postRefund`: reversal from merchant payable, or from bank settlement
  when the payment was already settled.
- `POST /v1/payments/{paymentId}/refunds` and `GET` list.
- `REFUND_PROCESSED` on `refund.events`; the existing operations consumer fans it out
  to merchant webhooks.
- Metrics: `refund_processed_total`, `refund_idempotency_replay_total`.

### Fixed

- Multiple partial refunds no longer attempt `REFUND_INIT` from `PARTIALLY_REFUNDED`,
  which the state machine correctly rejects. The transition is applied only on the
  first refund.

### Verified

- 314 tests across eight modules, 0 failures.
- Integration test: 400 then 600 on a 1000 payment yields `PARTIALLY_REFUNDED` then
  `REFUNDED`, merchant payable 600 then 0, debits equal credits, no unbalanced
  transactions.
- Over-refund and replay covered.

## [Fintech Gate 1] — 2026-10-26 — production double-entry ledger

### Added

- `ledger_line` append-only table: transaction id, account, debit, credit, currency,
  reference type and id, posted at. No setters, no update path.
- `LedgerAccount`: customer funds, merchant payable, platform fee, GST payable, bank
  settlement, platform receivable.
- `LedgerService`: balanced pair posting, idempotent on `(transaction, credit account)`,
  capture posting, settlement posting with component sum validation.
- `LedgerInvariantChecker`: per-transaction balance query, global debit-credit delta,
  gauge `ledger_unbalanced_transactions`, ShedLock-scheduled.
- `POST /internal/ledger/settlement` on payment-service; operations calls it through
  `SettlementIntegrationGateway` after a successful bank transfer.
- Capture and auto-capture now post `CUSTOMER_FUNDS -> MERCHANT_PAYABLE`.

### Verified

- 304 tests across eight modules, 0 failures.
- Capture integration test asserts a balanced two-line posting for the payment.
- Settlement executor test asserts the ledger call.
- Replay of the same pair is a no-op.

## [Ownership] — 2026-10-26

### Changed

- Every package moved to `com.snehal.neopay` with shortened module names:
  `common`, `gateway`, `config`, `discovery`, `merchant`, `payment`, `vault`,
  `operations`. Maven coordinates, auto-configuration imports, ArchUnit patterns and
  scripts updated across 331 files.
- README now has a `Lineage` section naming the reference implementation and listing
  the original work in this repository.
- `PaymentService`, `OrderService` and `PaymentLookupService` accept and return value
  objects (`MerchantId`, `PaymentId`, `OrderId`). Controllers convert UUIDs at the
  boundary, so the HTTP contract is unchanged.

### Removed

- Stale idempotency TODO.
- Refund TODO replaced by a documented `NO_REFUNDS_RECORDED` constant.
- Commented-out query blocks in `OrderServiceImpl` and `PaymentServiceImpl`.

### Verified

- 294 tests across eight modules, 0 failures.
- `grep` over main sources returns no TODO, FIXME, XXX or HACK.
- No `com.snehal` reference remains in source or build files.

## [Coverage closure] — 2026-10-26

### Added

- Rate limiter tests: token bucket, sliding window, sliding window Lua, including
  Redis failure fail-open and retry-after computation.
- Cache tests: Redis API key cache hit, miss, serialization failure, eviction.
- Common-lib tests: HMAC signer with an RFC 4231 vector, auditor fallback chain,
  Kafka topic resolution, merchant context and MDC filters, AES round trip.
- Payment tests: UPI and net banking adapters, order service, order persistence,
  payment lookup, bank callback simulator, payment outbox result handler, capture
  success and failure, controllers.
- Operations tests: settlement engine, outbox poller, outbox result handler, webhook
  retry queue, DLQ recorder, Kafka consumer, delivery scheduler.
- Gateway tests: API key authentication handler, including grace-period secret,
  cache hit, malformed header and rate limiting.
- Vault tests: charge success and processor failure, controller.
- Customer service tests: blank email, reuse, create, missing merchant.

### Measured

- Tests: 304 across eight modules, 0 failures.
- Line coverage: 80.3% (1,443 of 1,798 lines).
- Per module: gateway 76.8%, common-lib 77.2%, merchant 79.7%, operations 85.1%,
  payment 81.0%, vault 76.5%.

### Added

- `scripts/e2e-full-flow.sh`: full-stack check from signup through API key, order,
  tokenization, idempotent payment, capture, outbox drain, and delivered webhook.
  Requires the local stack; not executed in this environment.

### Note

`PaymentAggregate` remains the authority for money invariants, not for state
transitions: routing transitions through the aggregate created a package cycle with
`statemachine`, which ArchUnit rejects. The state machine stays the transition
authority; the aggregate stays the money authority.

## [Post-gate] — 2026-10-25 — simulation, publication, behaviour, coverage

### Changed

- `FeeSchedule` and `SettlementFeeCalculator` moved to `common-lib` `money` package so
  payment and operations share one fee implementation.
- `PaymentSimulator` now calls `FeeSchedule.standardIndia()` and `PaymentStateMachine`
  instead of reimplementing either. Settlement precedes refund, matching the machine:
  `SETTLED → PARTIALLY_REFUNDED → REFUNDED`.
- `OrderRecord` setters removed: `markAttempted`, `markPaid`, `markCancelled`.
- `Settlement` setters removed: `markTransferPending`, `markProcessed`, `markFailed`.
- Webhook HTTP transport extracted behind `WebhookSender`; `RestClientWebhookSender`
  is the adapter. Delivery policy is testable without a server.

### Added

- `docs/blog/deterministic-payment-simulation.md`: publication-ready article.
- `docs/PUBLISHING.md`: channel options and checklists.
- `MerchantApiTest`: signup, login, API key lifecycle, webhook CRUD, validation,
  unknown key.
- `WebhookDeliverExecutorTest`: delivered, retry, DLQ, transport exception, terminal
  event, payload forwarding.

### Measured

- Tests: 199 across eight modules, 0 failures.
- Line coverage: 50.8% (903 of 1778 lines), up from 24.8%.
- merchant-service: 29.7% → 70.3%.
- operations-service: 29.7% → 41.7%.
- Payment simulation: 10M transactions per seed, 0 violations, production rules.

### Not done

- 80% coverage target not met.
- Article not published externally; no account configured.

## [Gate 11] — 2026-10-25 — deterministic simulation

### Added

- `PaymentSimulator`: in-memory, seeded, single-threaded payment lifecycle with
  duplicate keys, authorization failure, capture, partial or full refund, and
  settlement with fee and GST. Every movement is a double-entry posting.
- `Ledger`: debit and credit accumulation, balance per account, balance check.
- `SimulationMain`: CLI taking seed and transaction count, exiting non-zero on any
  violation.
- `PaymentSimulationTest`: one million transactions hold every invariant; same seed is
  deterministic; different seeds diverge.
- `docs/SIMULATION.md`: model, invariants, results, reproduction, and explicit limits.

### Verified

- Seed 42: 10,000,000 transactions in 2,590 ms, 0 violations.
- Seed 2026: 10,000,000 transactions in 2,350 ms, 0 violations.
- Debits equal credits in both runs; final merchant payable balance is zero.

### Not done

- Not published externally. The write-up and harness are repository artifacts; a
  public post or conference submission requires an external account.
- The simulator mirrors the rules; it does not call `SettlementFeeCalculator` or
  `PaymentStateMachine`. Divergence between model and implementation remains possible.
- No concurrency or clock-skew faults in the simulator.

## [Gate 2] — 2026-10-25 — Payment behaviour (partial)

### Changed

- `Payment` no longer exposes `@Setter`. State changes go through intention-revealing
  methods: `transitionTo`, `recordBankReference`, `recordProcessorReference`,
  `recordFailure`, `markAuthorized`, `markCaptured`, `markFailed`, `markSettled`,
  `markRefunded`.
- All four call sites updated: `PaymentTransitionService`,
  `PaymentAuthorizationRecorder`, `PaymentLookupServiceImpl`, `PaymentServiceImpl`.

### Not done

- `OrderRecord` and `Settlement` still expose setters.
- Value objects are used in the aggregate and tests, not in service or controller
  signatures.

## [Gate 3] — 2026-10-25 — coverage expansion (partial)

### Added

- `GlobalExceptionHandlerTest`: ten handlers, status and `errorCode` assertions.
- `IdempotencyFilterTest`: pass-through, claim, store, replay, conflict.
- `PublicRouteMatcherTest` and `GatewayAuthFilterTest`: public routes, missing header,
  Basic, Bearer, rate limit, authentication failure, unexpected failure.
- `SettlementTransactionExecutorTest`: empty, fee computation, bank failure, resolve
  success, resolve failure, already resolved.

### Measured

- Line coverage: 43.1% (723 covered, 955 missed), up from 24.8%.
- Tests: 175 across eight modules, 0 failures.

### Not done

- 80% line coverage target is not met. Largest gaps: operations 29.7%, merchant 29.7%,
  common-lib 45.6%.
- No cross-service end-to-end test covering settlement and webhook delivery in one run.

## [Gate 5] — 2026-10-25 — event store, replay and snapshots

### Added

- `domain_event` table: `aggregate_id`, `sequence_number`, `event_type`,
  `schema_version`, JSON `payload`, `occurred_at`, unique
  `(aggregate_id, sequence_number)`.
- `DomainEventStore`: append with optimistic concurrency, load with upcasting.
- `ActorUpcaster`: upgrades `PAYMENT_TRANSITION` v1 payloads to v2 by adding `actor`.
- `PaymentEventAppender`: one event per transition, same transaction as the status
  change; snapshot every ten events.
- `PaymentEventReplayer`: rebuilds status from the latest snapshot plus later events.
- `PaymentTransitionService` appends an event for every transition.

### Verified

- Four transitions replay to `CAPTURED` with four stored events.
- Stale expected version rejected with `OptimisticConcurrencyException`.
- Snapshot written at sequence ten and used by replay.
- Version one payload upcast with actor `SYSTEM`.
- 143 tests across eight modules, 0 failures.

### Not done

- No projection rebuild into a separate read table; replay returns the status, it does
  not rewrite `payment`.
- Only `PAYMENT` aggregates are event-sourced.

## [Gate 7] — 2026-10-25 — performance

### Added

- `PaymentLatencyTest`: 300 samples after 50 warmup iterations against Postgres 16,
  gateway mocked. Measured p50 6 ms, p95 9 ms, p99 11 ms. Fails above 300 ms.
- `perf/k6-payment.js` with HTTP thresholds.
- `docs/PERFORMANCE.md`: targets, method, results, index rationale, profiling
  procedure, measurement limits.

### Changed

- `show-sql: false`, `open-in-view: false`.
- Hikari bounded at 20 connections, 5 idle, 3-second connection timeout.
- JVM flags: `MaxRAMPercentage=75`, Generational ZGC.

### Fixed

- Missing composite indexes on `(status, created_at)` for `outbox_event` and
  `payment`; both are scanned by scheduled jobs every five seconds.

### Not done

- No committed flame graph; profiling procedure documented only.
- Measurement is service-level with a mocked gateway, not the full HTTP path.

## [Gate 10] — 2026-10-25 — documentation

### Added

- `docs/adr/0001`–`0008`: outbox, ShedLock, idempotency, saga compensation, database
  per service, tokenization, native config server, staged event sourcing.
- `docs/c4/context.md`, `docs/c4/container.md`, `docs/c4/component-payment.md` with
  Mermaid diagrams.
- `docs/runbook.md`: outbox stall, webhook DLQ, Redis loss, Kafka lag, settlement
  failure, idempotency conflict storm, payment stuck in `AUTHORIZING`.
- `docs/POSTMORTEM.md`: six real defects with symptom, root cause, blast radius, fix
  and prevention.
- `docs/DESIGN.md`: invariants table, consistency model, designed failure modes,
  non-goals, rejected alternatives, weak points.
- README: test layers, quality gates, documentation index, updated gaps.

## [Gate 12] — 2026-10-25 — complexity budget

### Added

- `pmd-ruleset.xml` and `maven-pmd-plugin` bound to `verify` in every module:
  cyclomatic complexity ≤10 per method, parameters ≤5, methods ≤25, fields ≤20,
  coupling between objects ≤20.
- ArchUnit rule asserting at most five raw parameters per method.
- `scripts/complexity-budget.sh`: source files ≤300 lines.

### Changed

- `IdempotencyFilter`: response persistence extracted to `persistOutcome`.
- `PaymentAggregate`: refund guard extracted; six-argument `rehydrate` replaced by a
  `PaymentSnapshot` record.

### Fixed

- `IdempotencyFilter.replay` continued into `substring` after resolving a missing
  separator, throwing `StringIndexOutOfBoundsException`. Now returns.

## [Gate 2] — 2026-10-25 — domain model (partial)

### Added

- `Currency` enum with ISO 4217 minor-unit scale: INR 2, USD 2, EUR 2, GBP 2, JPY 0,
  KWD 3.
- `Money.scale()`, `Money.majorUnits()` and `Money.isGreaterThan()`. Currency
  validation routes through `Currency`.
- `PaymentId`, `MerchantId`, `OrderId` value objects with null rejection.
- `PaymentAggregate` enforcing money invariants: capture only from
  `AUTHORIZED`/`CAPTURING`, capture never exceeds authorization, refund only after
  capture, cumulative refund never exceeds capture, non-positive amounts rejected.
- `FeeSchedule` value object (fee bps, GST bps, rounding mode);
  `SettlementFeeCalculator` delegates to `FeeSchedule.standardIndia()`.
- 15 tests across `Currency`, `FeeSchedule` and `PaymentAggregate`.

### Not done

- Entity setters remain. `Payment`, `OrderRecord` and `Settlement` are still the
  persistence projection; the aggregate is not yet the write path.
- Raw `UUID` identifiers remain in service and controller signatures.
- `Money` is stored as `(amountUnits, currency)`; scale is derived, not persisted.

## [Gate 8] — 2026-10-25 — observability in code

### Added

- `logstash-logback-encoder` 8.0 and a `logback-spring.xml` in every service that
  imports common-lib. Console output is JSON with service name.
- `MdcLoggingFilter` at `HIGHEST_PRECEDENCE + 2` adding `path`, `method`,
  `merchantId` and `keyId` to the MDC, cleared in `finally`.
- `PaymentMetrics`: `payment_initiated_total{method}`, `payment_captured_total{method}`,
  `payment_failed_total{reason}`, `payment_idempotency_replay_total`,
  `outbox_pending_count`, `outbox_lag_seconds`.
- `WebhookMetrics`: `webhook_delivery_total{status}` for `DELIVERED`, `FAILED`, `DEAD`.
- `PanLoggingTest`: asserts a PAN never appears in log output during tokenization.
- `PaymentMetricsTest`: counter tags, null-reason normalisation, zero-valued gauges.

### Verified

- All eight modules pass: 132 tests, 0 failures, 0 errors.
- Counter tags are bounded: method has four values, reason is a fixed error code.

### Not done

- Trace propagation across Feign and Kafka headers is not asserted by a test.
- Log aggregation and retention are infrastructure concerns, out of scope here.

## [Gate 6] — 2026-10-25 — API contract

### Added

- RFC 9457 `application/problem+json` error responses in `GlobalExceptionHandler` with
  `type`, `title`, `status`, `detail`, `instance`, plus `errorCode`, `timestamp`, and
  `fieldErrors` extension members. Gateway authentication and rate-limit rejections
  emit the same shape.
- Pagination on `GET /v1/merchants/api-keys` and `GET /v1/merchants/webhooks`:
  `page`, `size`, `sort`, database-level `Pageable`, default size 20 and
  `createdAt,desc`.
- springdoc-openapi 3.0.0 in merchant, payment, vault and operations services.
  OpenAPI 3.1 served at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.
- `docs/openapi/payment-service.json` snapshot and a regeneration flag.
- `docs/API.md`: versioning policy, error contract, pagination envelope, idempotency
  rules, coverage matrix, breaking-change checklist.
- `OpenApiContractTest` asserting the generated document exposes the order and payment
  paths.

### Changed

- `ApiKeyService.listByMerchant` and `WebhookConfigService.list` return `Page` and
  accept `Pageable`.
- ArchUnit import options exclude test classes.

### Not done

- Spec diff gate in CI (no pipeline exists yet).
- Committed snapshots for merchant, vault and operations services.

## [Gate 4] — 2026-10-25 — property-based tests

### Added

- jqwik 1.9.2 in `common-lib`, `payment-service`, and `operations-service`.
- `MoneyPropertyTest`: add/subtract identity for non-overflowing values, basis-point
  result bounded by exact floor and ceiling, zero rate yields zero, full rate is
  identity.
- `SettlementFeePropertyTest`: components always sum to gross, components
  non-negative, fee monotonic in gross, fee never exceeds gross.
- `PaymentStateMachinePropertyTest`: SETTLED unreachable without CAPTURED, CAPTURED
  unreachable without AUTHORIZED, SETTLED remains refundable, over generated event
  sequences of up to 30 events.

### Verified

- Approximately 66,000 generated cases per run.
- All eight modules verify: 106 tests, 0 failures, 0 errors.

## [Gate 3] — 2026-10-25 — test pyramid (partial)

### Added

- Testcontainers 1.20.4 in `merchant-service` and `payment-service`.
- `ApiKeyIntegrationTest`: real Postgres and Redis. Create, lookup, revoke, rotate
  round trips.
- `PaymentFlowIntegrationTest`: real Postgres, mocked gateway router. Initiate,
  authorize, capture, assert `CAPTURED`, order `PAID`, outbox rows present.
- `PaymentIdempotencyConcurrencyTest`: 50 concurrent inserts with the same
  `(merchant_id, idempotency_key)`. Exactly one succeeds, 49 rejected by the unique
  index, one row remains.
- JaCoCo 0.8.12 report in all modules.

- 31 unit tests across `common-lib`, `payment-service`, `vault-service`, and
  `merchant-service`.
- Idempotency store tests: claim, duplicate claim, fail-open on Redis failure,
  prefixed keys, miss handling, delete.
- Rate limiter tests: window start, within-limit, over-limit, null and zero TTL
  fallback, null increment fail-open.
- Payment initiation tests: replay short-circuits the gateway, gateway exception
  compensates instead of propagating, success applies the result, null key skips
  replay, replay stable across calls.
- Vault tests: BIN and last-four extraction, masked persistence, token prefix,
  brand detection for Visa, Mastercard, Amex, RuPay, unknown token rejection.
- API key tests: hash-only storage, unknown merchant rejection, revoke disables and
  evicts, rotate preserves the previous hash and sets a 24h grace period, disabled
  key cannot rotate, cache entry mapping.
- PIT 1.19.0 with `pitest-junit5-plugin` 1.2.2, scoped to `Money`,
  `FixedWindowRateLimiter`, and `RedisIdempotencyStore`.

### Verified

- Mutation coverage: 34 mutations, 31 killed, 91%, threshold 80.
- Suite executed three times consecutively: 95 tests, 0 failures, 0 errors.

### Not done

- Full-stack e2e across service boundaries: settlement and webhook delivery are not
  exercised in the same test as payment capture.
- Coverage gate at 80% line coverage. Measured: 24.8% across common-lib and the four
  business services (409 covered, 1238 missed).

Gate 3 remains open on those two items.

## [Gate 9] — 2026-10-25 — static analysis

### Added

- ErrorProne 2.36.0 as a javac plugin in all eight modules, with a curated set of
  checks promoted to build failures.
- SpotBugs 4.8.6.6 at effort Max and threshold Medium, bound to `verify`.
- `.mvn/jvm.config` exporting the `jdk.compiler` packages ErrorProne requires.
- `spotbugs-exclude.xml` with one documented exclusion for Spring-injected
  collaborators and JPA associations.

### Fixed

- `FixedWindowRateLimiter` used non-short-circuit `&` when checking a nullable TTL,
  which would unbox null and throw NPE while computing `Retry-After`.
- `BankCallbackSimulator` bucketed on `Math.abs(hashCode())`; `Integer.MIN_VALUE`
  keeps a negative absolute value, producing a negative bucket or delay. Both sites
  now use `Math.floorMod`.
- `PaymentServiceImpl` bound an unused pattern variable in two `instanceof` checks.
- `WebhookDeliverExecutor.MAX_ATTEMPTS` was an instance field holding a constant.

## [Gate 1] — 2026-10-25 — package boundaries

### Added

- `archunit-junit5` in common-lib and the five service modules.
- `ArchitectureTest` per module enforcing: no package cycles, controllers must not
  reach repositories, repositories must not depend on services, entities must not
  depend on web or repositories, services must not depend on controllers, no
  `@Autowired` field injection, and common-lib must not depend on any service.

### Fixed

- `InternalApiKeyController` injected `ApiKeyRepository` directly. Lookup moved to
  `ApiKeyService.findCacheEntry`; the controller now depends only on the interface.

## [Gate 0] — 2026-10-25 — SDE defect baseline

### Fixed

- Settlement fee arithmetic used `double` for a 2% rate. `0.02` is not representable in
  binary, so amounts congruent to 25 mod 100 paise were undercharged by one paisa
  (1 in 100 amounts). Replaced with integer basis points and explicit HALF_UP rounding.
- `Money.add` and `Money.subtract` used raw `int` arithmetic and wrapped silently on
  overflow. Both now use `Math.addExact` / `Math.subtractExact`.
- `Money` accepted any non-null currency string. The factory now trims, uppercases,
  validates against a supported set, and rejects null or blank values.
- `PaymentLookupServiceImpl.markSettled` mutated `PaymentStatus` directly. Settlement now
  transitions through `PaymentTransitionService`, writes a transition log row, and is
  subject to the same guards as every other transition.
- Settlement gross totals accumulated with `Integer::sum`, which can overflow silently.
  Accumulation is now `long` with `Math.toIntExact`.

### Added

- `SettlementFeeCalculator` with integer basis-point rates and HALF_UP rounding.
- `Money.multiplyByBasisPoints(int, RoundingMode)` using exact decimal division.
- `PaymentStateMachine.canTransition`, `requireTransition`, `allowedTransitions`, and
  `transitionCount`; the allowed-status matrix is derived from the event table so the
  two representations cannot diverge.
- 20 tests: fee boundaries at 25, 125, 225, 525, 10025 and 100125 paise; component-sum
  invariant; Money normalization, overflow, and null handling; exhaustive 11x11
  transition matrix; skip-state and self-transition rejection.

### Verified

- All seven modules green: 27 tests, 0 failures, 0 errors.

## [Unreleased] — 2026-10-24

### Added

- Converted the project to the full `neopay-reference-pro` architecture:
  - `api-gateway-service`
  - `discovery-service` (Eureka)
  - `config-service` (Spring Cloud Config)
  - `merchant-service`
  - `payment-service`
  - `vault-service`
  - `operations-service`
  - `common-lib`
- Per-service PostgreSQL databases: merchant, payment, vault, operations.
- Gateway-level Basic API-key and JWT authentication with identity-header injection.
- Gateway routing through Eureka (`lb://...`) and configurable public routes.
- Feign clients for gateway → merchant, payment → merchant/vault, and operations → merchant/payment.
- Resilience4j circuit breakers and retries on cross-service calls.
- Redis ShedLock on all scheduled jobs.
- Kafka transactional outbox per owning service, with manual acknowledgements in the webhook consumer.
- Operations webhook fan-out, Redis retry queue, backoff, DLQ, and reconciliation.
- Vault envelope encryption and internal token-charge endpoint.
- Native Config Server with bundled service configuration under `config-service/src/main/resources/config/`.
- Local infrastructure compose file with Postgres (four DBs), Redis, Kafka, and Zipkin.
- `build-all.sh`, `scripts/run-local.sh`, and `scripts/stop-local.sh`.
- Kubernetes manifests, Prometheus/Grafana/Zipkin setup, and Jib image configuration retained.
- README and in-depth FEATURES inventory for the distributed architecture.

### Changed

- Service sources use Java 21 (reference required Java 25).
- External GitHub Config Server replaced with a self-contained native config repository.
- Jib image publishing is disabled by default (`jib.skip=true`) and uses neutral `neopay/...` image names.
- Feign URLs default to Eureka load-balancing instead of unresolved environment placeholders.
- Local dev secrets replaced with randomized development-only values; k8s secret file sanitized.

### Fixed

- Merchant and order response status fields now map correctly through MapStruct.
- API-key revoke route uses `/{keyId}` instead of a literal `/keyId`.
- Kafka listener placeholders now use the singular configured topic keys.
- Local merchant webhook encryption key typo (`webhoook` → `webhook`).
- Vault charge Feign response now uses a concrete `VaultChargeResponse` with a `type` discriminator, fixing cross-service deserialization.
- Kafka consumer `ack-mode: manual` added so `Acknowledgment` injection works.
- `NETBANKING` simulator configuration matches the enum value.
- Test profiles for every service (H2, config/discovery disabled, trace endpoint) make `mvn test` runnable without infrastructure.

### Verification

- All seven service test suites pass.
- Live end-to-end run through the gateway:
  - signup/login/API key
  - order
  - vault tokenization
  - card payment via payment → vault
  - scheduled auto-capture (`CAPTURED`, order `PAID`)
  - payment idempotency
  - outbox → Kafka → webhook delivery (`DELIVERED`, 204)
  - ShedLock keys in Redis
- Docker stack destroyed with `docker compose down -v`.

### Known limitations

- Refund service/API/processor not implemented.
- Wallet payments not implemented.
- Order/payment read APIs are minimal.
- No refresh-token/logout/password-reset flows.
- No admin replay for failed outbox rows or dead webhooks.
- No automated Testcontainers end-to-end suite.
- Internal endpoints are unauthenticated; rely on cluster network isolation.
