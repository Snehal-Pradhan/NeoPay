# Pricing and Fee Resolution

## Model

| Layer | Source | Scope |
|---|---|---|
| Default rule | `merchant_fee_schedule` | one row per merchant: MDR, GST, TDS, reserve in basis points |
| Method override | `merchant_method_pricing` | one row per `(merchant, method, effective_from)`; adds a fixed fee and a fee cap |
| Fallback | `PricingTerms.standardIndia()` | 200 bps MDR, 1800 bps GST, 1000 bps TDS, 500 bps reserve |

Resolution order for a settlement on date `D`:

1. Default rule for the merchant, else the standard rule.
2. The newest applicable override per payment method whose window contains `D`.
3. A method with no override uses the default rule.

## Arithmetic

| Component | Formula | Applied to |
|---|---|---|
| Variable fee | `gross × fee_bps / 10000`, half-up | gross per method group |
| Fixed fee | `+ fixed_fee_units` | per settlement |
| Cap | `min(fee, max_fee_units)` when the cap is non-zero | per method group |
| GST | `fee × gst_bps / 10000` | the capped fee |
| TDS | `fee × tds_bps / 10000` | the capped fee |
| Reserve | `base × reserve_bps / 10000` | the post-debt settlement base |
| Net | `base − fee − gst − tds − reserve` | merchant payout |

Reserve is merchant-level risk, not a processing fee, so it is computed once on the
post-debt base with the default rule rather than per payment method.

## Grouping

Payments are grouped by `paymentMethod` from `PaymentSettlementView`. Fees are computed per
group and summed. With no overrides every group resolves to the same rule, so the aggregate
equals the previous single-schedule arithmetic; a regression test pins the numbers.

## Rounding

`RoundingMode.HALF_UP` in minor units, applied per step. Integer arithmetic on `long`
minor units, `BigDecimal` only inside `Money.multiplyByBasisPoints`. No `double`.

## Effectivity

`merchant_method_pricing.effective_from` / `effective_to` are inclusive dates. The resolver
query filters by the settlement date and orders by `effective_from` descending, taking the
first row per method. Future-dated rows do not affect today's settlement.

## Tests

| Test | Covers |
|---|---|
| `PricingTermsTest` | fixed fee, cap, uncapped, GST/TDS on the capped fee |
| `SettlementPricingTest` | regression to legacy totals, method isolation, fallback, zero groups |
| `SettlementTransactionExecutorTest` | end-to-end settlement fields |
| `SettlementCarryForwardTest` | debt reduces the base; reserve follows the reduced base |

## Limitations

- No per-currency pricing. One rule set per merchant; the settlement currency is whatever the
  payments convert to.
- No tiered or volume-based rates.
- No pricing admin API. Rows are inserted by a migration or an operator, not through
  `merchant-service`.
- No tax jurisdiction matrix; GST is a single rate per rule.
