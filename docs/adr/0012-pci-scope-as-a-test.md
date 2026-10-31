# ADR 0012: PCI scope enforced by a test

**Status:** Accepted

## Context

The card-data boundary was documented in `PCI_SCOPE.md` and `ADR 0006`, but a new field named `pan` in any payment entity would pass review. A written boundary decays; a failing build does not.

## Decision

Enforce the boundary in the build.

- `PciScopeTest` uses ArchUnit to reject a non-static `String` field matching `(?i)^pan.*` in `com.snehal.neopay.payment..`.
- A second rule rejects a dependency from `payment-service` on the vault card entity.
- `scripts/pci-scope-check.sh` greps merchant, payment, operations, and gateway sources for a `String pan` field.
- The script fails if `TokenizeRequest.pan` disappears, so the gate cannot pass by scanning nothing.

Source: `payment-service/src/test/java/com/snehal/neopay/payment/PciScopeTest.java`, `scripts/pci-scope-check.sh`.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Documentation review only | No enforcement; drift is invisible |
| Static analysis rule in PMD | Custom rule maintenance for one invariant |
| Database-level column grants | The risk is a field in the application, not a grant |

## Consequences

- Adding card fields to the payment service fails `mvn verify`.
- The rule permits constants such as `PAN_CARD_DECLINED`; it targets instance fields.
- The transient PAN in `PaymentProcessorRequest` (common-lib) stays outside the gate and is documented as an exception.
- The gate does not prove the absence of PAN in logs or traces; masking is asserted separately in DTO tests.
