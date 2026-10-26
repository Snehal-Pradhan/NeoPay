# Compliance and data-scope statement

This document states what the system does and does not handle. It is a scope
statement, not a certification.

## Cardholder data

| Location | Data | Control |
|---|---|---|
| Browser / merchant client | PAN typed by the payer | Never sent to `merchant-service` or `payment-service` |
| `api-gateway-service` | Authorization header, token | PAN not accepted on any gateway route |
| `vault-service` | PAN, encrypted | AES-GCM envelope encryption; per-card DEK encrypted by a master key; PAN never logged |
| `vault-service` response | `tok_...`, last four, brand | Only tokenized reference leaves the service |
| `payment-service` | Token, amount, status | No PAN, no CVV stored |
| Logs | Masked PAN (`****1234`), no CVV | Masking tests in `common-lib` and `vault-service` |

Tokenisation reduces the PCI DSS scope but does **not** remove it: `vault-service`
stores cardholder data and is therefore in scope for the requirements that apply to
stored data (encryption at rest, key management, access control, audit logging).

## Key management

- Master key comes from `VAULT_MASTER_KEY` (environment or secret manager); key length
  is validated at startup.
- In production the master key must live in a KMS/HSM, be rotated on a schedule, and
  never be stored in Git. The repository ships only placeholders.
- Webhook secrets are encrypted with `WEBHOOK_SECRET_ENCRYPTION_KEY`; JWT signing uses
  `JWT_SECRET`.

## What is simulated

| Component | Reality |
|---|---|
| Card processor (`CardPaymentProcessor`, UPI/net-banking adapters) | Deterministic test PANs; no PSP is contacted |
| Bank settlement callback | Opt-in scheduler (`payment.simulator.enabled`, `operations.settlement.simulator-enabled`), disabled by default |
| Reconciliation statements | CSV uploaded by an operator or test; no bank SFTP feed |
| Webhooks | Real HTTP delivery to a configured merchant URL, with retries and a dead-letter record |

No real money moves. The simulators are opt-in so they cannot run in an environment
that did not ask for them.

## Gaps an auditor would still raise

- No PCI DSS SAQ, penetration test or ASV scan.
- No field-level encryption for merchant PII (PAN/GST identifiers stored as plain text).
- No key rotation automation or HSM integration.
- No data-retention or erasure workflow.
- No centralised audit trail of operator actions.
- No regulatory reporting (RBI/localisation, settlement file attestation) beyond the
  internal reconciliation attestation endpoint.

## CI evidence

- `secret scan`: gitleaks on full history.
- `manifests`: kubeconform plus Trivy misconfiguration scan.
- Per-project SBOM (`bom.json`, CycloneDX) published as a build artifact.
- Dependabot watches Maven dependencies and GitHub Actions weekly.
