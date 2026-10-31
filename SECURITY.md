# Security policy

## Supported versions

The `main` branch is the only supported line. Tags are not maintained.

## Reporting a vulnerability

Use GitHub private vulnerability reporting on this repository: Security, then Advisories, then Report a vulnerability. Do not open a public issue for an unfixed defect.

Include the affected commit, a reproduction, and the impact you can demonstrate. Response target: seven days for an initial assessment.

## Scope

| In scope | Out of scope |
|---|---|
| Authentication and authorization bypass | Denial of service through enormous payloads |
| Card data exposure | Findings in third-party images without a demonstrated path |
| Ledger or balance corruption | Missing rate limit on an internal endpoint |
| Idempotency bypass that duplicates a charge | Demo credentials committed on purpose |

## Design controls

| Control | Location |
|---|---|
| Card data confined to `vault-service` | [docs/PCI_SCOPE.md](docs/PCI_SCOPE.md), `PciScopeTest` |
| Inbound webhook HMAC | `StripeWebhookVerifier` |
| API key hashing | `merchant-service` authentication module |
| Idempotency uniqueness | database constraints, [docs/03_patterns/03_idempotency.md](docs/03_patterns/03_idempotency.md) |
| Secret scanning | Gitleaks job in CI |

## Secrets

No secret belongs in the repository. Local values come from `.env` files that are gitignored; the example file is `k8s/k8s-secrets.env.example` and contains placeholders only.
