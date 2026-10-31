# PCI Scope — SAQ-A Argument

## Claim

NeoPay's merchant-facing surface is **SAQ-A**: the merchant never sends card data to
NeoPay's payment, merchant, operations or gateway services, and those services never store
a PAN. Card data is accepted only by `vault-service`, replaced by a token, and the PAN is
decrypted only inside the processor call that forwards it.

## Data classes

| Class | Example | Where it lives | Persisted |
|---|---|---|---|
| PAN | `4111111111111111` | `vault-service` request DTO, transient local | No |
| Encrypted PAN | ciphertext under a per-card DEK | `vault_card.encrypted_pan` | Yes, ciphertext only |
| Token | `tok_9f2c...` | `vault-service` response; every other service | Yes |
| Last four + brand + BIN | `1111`, `VISA`, `411111` | `vault_card`, payment response | Yes |

## Flow

| Step | Actor | Data crossing |
|---|---|---|
| 1 | Merchant SDK → `POST /v1/vault/tokens` | PAN, expiry, name |
| 2 | `vault-service` | derives BIN, last four, brand; encrypts PAN with a per-card DEK; wraps DEK with the master key |
| 3 | `vault-service` → caller | token only |
| 4 | `payment-service` → processor | token; `vault-service` resolves the PAN internally for the processor call and zeroes the byte array afterwards |
| 5 | persistence | token, last four, brand; never a PAN |

## Enforcement

| Gate | Type | Rule |
|---|---|---|
| `PciScopeTest` | ArchUnit, runs in `mvn verify` | no non-static `String` field matching `(?i)^pan.*` in `com.snehal.neopay.payment..` |
| `scripts/pci-scope-check.sh` | source grep, local and CI-ready | no `String pan` field in merchant, payment, operations or gateway services |
| positive control | same script | fails if `TokenizeRequest.pan` disappears, because then the gate proves nothing |

Deliberate exception: `PaymentProcessorRequest` in `common-lib` carries a PAN as a transient
in-memory field. It is never persisted, never logged (`toString` masks it) and never serialised
into an event payload.

## Logging

| Control | Implementation |
|---|---|
| Request DTO `toString` | masks everything except the last four digits |
| Processor request `toString` | masks the PAN |
| Vault response | token, last four and brand only |
| Byte hygiene | decrypted PAN bytes are zero-filled after the processor call |

## What this does not prove

- No QSA audit. This is an engineering argument, not an attestation.
- No network segmentation proof between services beyond Kubernetes network policies.
- Card data still transits `vault-service` memory, so `vault-service` remains in scope.
- Tokenisation reduces scope; it does not remove the obligation to protect the vault.

## Reproduction

```bash
./scripts/pci-scope-check.sh
mvn -f payment-service/pom.xml test -Dtest=PciScopeTest
```
