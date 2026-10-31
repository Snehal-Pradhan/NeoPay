# Chargeback Lifecycle

## States

| State | Meaning | Terminal |
|---|---|---|
| `OPENED` | issuer notified NeoPay; funds held | no |
| `EVIDENCE_REQUIRED` | merchant must respond before `evidence_due_at` | no |
| `UNDER_REVIEW` | evidence received, awaiting issuer decision | no |
| `REPRESENTED` | merchant contest filed with the acquirer | no |
| `WON` | issuer accepted the merchant position; hold released back | yes |
| `LOST` | issuer ruled for the cardholder; hold booked to receivable | yes |

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/payments/{paymentId}/disputes` | open a dispute; posts the hold |
| `GET` | `/v1/payments/{paymentId}/disputes` | list disputes for a payment |
| `POST` | `/v1/payments/{paymentId}/disputes/{disputeId}/evidence` | upload evidence |
| `POST` | `/v1/payments/{paymentId}/disputes/{disputeId}/represent` | file representment |
| `POST` | `/v1/payments/{paymentId}/disputes/{disputeId}/resolve` | close as `WON` or `LOST` |

All paths are merchant-scoped: a dispute belonging to another merchant is reported as
`404`, not `403`, so existence is not leaked.

## Ledger movement

| Transition | Debit | Credit |
|---|---|---|
| open | source account (`MERCHANT_PAYABLE` or `BANK_SETTLEMENT`) | `DISPUTE_HOLD` |
| `WON` | `DISPUTE_HOLD` | source account |
| `LOST` | `DISPUTE_HOLD` | `PLATFORM_RECEIVABLE` |
| evidence, representment | none | none |

Exactly two postings per dispute. The unique `(transaction_id, credit_account)` key in the
ledger makes a duplicated resolve a no-op rather than a double posting.

## Deadline sweep

`DisputeDeadlineScheduler` runs every 60 s (`dispute.evidence-sweep-ms`) under a ShedLock
lease. It selects `EVIDENCE_REQUIRED` disputes whose `evidence_due_at` has passed and
resolves them `LOST` with `outcome_reason = EVIDENCE_DEADLINE_MISSED`.

Idempotency: closing moves the row out of `EVIDENCE_REQUIRED`, so a second sweep cannot
post again. The integration test asserts one ledger pair after two sweeps.

## Evidence idempotency

`dispute_evidence` has a unique `(dispute_id, external_evidence_id)`. A retried upload with
the same client evidence id returns the current dispute without a second row or a second
state change. Uploads after the deadline are rejected with `EVIDENCE_WINDOW_CLOSED`;
representment without evidence is rejected with `EVIDENCE_NOT_SUBMITTED`.

## Events

| Event type | When |
|---|---|
| `DISPUTE_OPENED` | hold posted |
| `DISPUTE_EVIDENCE_REQUESTED` | deadline set |
| `DISPUTE_EVIDENCE_SUBMITTED` | evidence stored |
| `DISPUTE_REPRESENTED` | representment filed |
| `DISPUTE_RESOLVED` | terminal outcome, hold released |

All published through the transactional outbox with aggregate type `PAYMENT`.

## Tests

| Test | Covers |
|---|---|
| `DisputeEvidenceLifecycleTest` | evidence window, idempotent upload, representment gate, deadline sweep, ownership, terminal rejection |
| `DisputeServiceBehaviorTest` | open, validation, resolve, publish |
| `DisputeConcurrencyTest` | concurrent resolves post once |
| `DisputeControllerTest` | response mapping for every endpoint |

## Limitations

- No scheme-specific reason codes or evidence bundles (Visa CE3.0, Mastercard MDES).
- No acquirer file exchange; resolution is recorded internally.
- No partial dispute amounts per component; one amount per dispute.
- Evidence payload is a summary string, not a document store.
