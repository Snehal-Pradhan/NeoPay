# ADR 0006 — Tokenization to reduce PCI scope

**Status:** Accepted

## Context

Card data must never be stored or transported outside a controlled boundary.

## Decision

`vault-service` is the only component that sees a PAN. It encrypts the PAN with a
per-card data encryption key, wraps that key with a master key, and returns an opaque
`tok_...` token. `payment-service` stores and forwards only the token.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Store the PAN encrypted in the payment database | Expands PCI scope to the payment service and every backup of it |
| Delegate entirely to a PSP | Valid, but removes control over the token lifecycle |
| Hash the PAN | Irreversible; cannot charge or support card-on-file |

## Consequences

- `payment-service` is out of card-data scope.
- Token loss or key loss is unrecoverable; key rotation must be planned.
- A test asserts a PAN never appears in logs.
