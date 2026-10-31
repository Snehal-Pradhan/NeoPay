# ADR 0011: Per-method effective pricing

**Status:** Accepted

## Context

One global fee schedule cannot express a card rate that differs from UPI, a negotiated rate, or a rate change. Settlement already read a per-merchant default, but every payment method paid the same basis points.

## Decision

Two layers resolve pricing:

- `merchant_fee_schedule` holds the merchant default.
- `merchant_method_pricing` holds method-specific rows with `effective_from` and `effective_to`, a fixed fee, and a fee cap.
- `SettlementFeeCalculator` groups eligible payments by method, computes a fee per group, and sums.
- Reserve stays merchant-level and applies to the post-debt base, not per method.

Effectivity resolves by settlement date; the newest applicable row wins per method.

Source: `operations-service/src/main/java/com/snehal/neopay/operations/settlement/`.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| One row per merchant with method columns | Adding a method changes the schema |
| Versioned schedule without dates | No way to answer which rate applied on a past date |
| Per-payment fee calculation at capture | Rates can change before settlement; the settlement date is the contractual one |
| Reserve per method | Reserve is a merchant risk decision, not a processing fee |

## Consequences

- Grouping changes rounding when rates differ. With no overrides the aggregate equals the previous arithmetic, pinned by `SettlementPricingTest`.
- An override row must be created by a migration or an operator; no admin API exists.
- Currency is not part of the key; one rule set applies to every settlement currency.
