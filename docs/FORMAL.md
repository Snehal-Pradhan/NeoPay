# Formal Model

Source: `formal/PaymentLifecycle.tla` and `formal/PaymentLifecycle.cfg`.
Model checker: TLC, `tla2tools.jar` v1.7.1.
Recorded output: `formal/tlc-output.txt`.

## What is modelled

| Element | Representation |
|---|---|
| Lifecycle | `CREATED → AUTHORIZING → AUTHORIZED → CAPTURING → CAPTURED → SETTLED` |
| Failure | `AUTHORIZING → FAILED` |
| Refund | partial from `CAPTURED` or `SETTLED`; full moves to `REFUNDED` |
| Ledger | `ledgerDebit` and `ledgerCredit` incremented together at capture |

Amounts are bounded constants: `Amount = 10`, `PartialRefund = 5`.

## Invariants

| Invariant | Meaning |
|---|---|
| `TypeInvariant` | status is a known value; amounts stay in range |
| `MoneyConserved` | `ledgerDebit == ledgerCredit` always |
| `SettleRequiresCapture` | `settled` implies the full amount was captured |
| `RefundBounded` | cumulative refund never exceeds capture |
| `NoCaptureWithoutAuthorization` | capture only from captured-family states |
| `NoRefundBeforeCapture` | a refund implies a prior capture |

## TLC result

```
Model checking completed. No error has been found.
9 states generated, 9 distinct states found, 0 states left on queue.
The depth of the complete state graph search is 7.
Finished in 05s
```

Nine reachable states, depth seven, every invariant holds.

## Trace conformance

`ModelConformanceTest` asserts that every model edge is accepted by
`PaymentStateMachine` with the same target state. The model is a subset of the
implementation: the implementation has extra transitions (cancellation, auth expiry,
repeat partial refunds) that the model does not claim.

This is the direction that matters. If the model allows an edge the implementation
rejects, the model is wrong. If the implementation allows more, the model is
conservative.

## Reproduction

```bash
curl -sSL -o /tmp/tla2tools.jar \
  https://github.com/tlaplus/tlaplus/releases/download/v1.7.1/tla2tools.jar

cd formal
java -cp /tmp/tla2tools.jar tlc2.TLC \
  -config PaymentLifecycle.cfg -deadlock PaymentLifecycle.tla
```

Java conformance:

```bash
mvn -q -f payment-service/pom.xml test -Dtest=ModelConformanceTest
```

## Limits

- The model abstracts amounts to two constants; it is not a financial model.
- Concurrency and time are not modelled. Those are covered by the 50-thread database
  test and the deterministic simulator respectively.
- Refund-after-settlement ordering is modelled; fee, GST, TDS and reserve are out of
  scope here and covered by settlement tests.
- TLC explores nine states, which is small. That is deliberate: the point is the
  invariant set, not state-space size.

## Publication

The combined correctness story — deterministic simulation, formal model, conformance —
exists as a repository artifact and as a publication-ready article under `docs/blog/`.
External publication requires an account on GitHub Pages, Dev.to or a conference CFP.
