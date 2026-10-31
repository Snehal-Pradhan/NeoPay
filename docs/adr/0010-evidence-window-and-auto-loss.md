# ADR 0010: Evidence window with automatic loss

**Status:** Accepted

## Context

A chargeback requires a merchant response before the acquirer's deadline. Without a deadline the dispute stays open and the held funds stay frozen. The previous model had only `OPENED`, `UNDER_REVIEW`, `WON`, `LOST` and no notion of time.

## Decision

A dispute moves through `OPENED`, `EVIDENCE_REQUIRED`, `UNDER_REVIEW`, `REPRESENTED`, then a terminal `WON` or `LOST`.

- `requestEvidence` sets `evidence_due_at`.
- `submitEvidence` rejects a submission after the deadline with `EVIDENCE_WINDOW_CLOSED`.
- A scheduled sweep runs every 60 seconds under a ShedLock lease.
- The sweep resolves expired disputes as `LOST` with `outcome_reason = EVIDENCE_DEADLINE_MISSED`.
- Every terminal outcome posts exactly one ledger pair.

Source: `payment-service/src/main/java/com/snehal/neopay/payment/dispute/`.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Manual resolution only | Deadlines pass silently; funds stay held |
| Auto-close as `WON` on expiry | Credits the merchant without evidence |
| Extend the deadline on every sweep | Hides an unanswered dispute indefinitely |
| Model the deadline in a separate table | Two sources of truth for one state |

## Consequences

- An unanswered dispute costs the merchant the amount, which matches scheme behaviour.
- The sweep is idempotent because a closed dispute leaves `EVIDENCE_REQUIRED`.
- Evidence is a summary string, not a document bundle.
- Clock skew between the scheduler and an API call can decide a boundary case; both use the application clock.
