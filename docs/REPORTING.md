# Reporting and Retention

## Reports

| Report | Source | Endpoint | Deterministic |
|---|---|---|---|
| Settlement | ledger lines with reference type `SETTLEMENT` | `GET /internal/reports/settlement?date=YYYY-MM-DD` | yes |
| Tax | `GST_PAYABLE` and `TDS_PAYABLE` lines | `GET /internal/reports/tax?date=YYYY-MM-DD` | yes |
| Recon attestation | recon run plus breaks | `GET /internal/recon/runs/{id}/attestation` | yes |
| Settlement statement | settlement record | `GET /internal/settlements/{id}/statement` | yes |
| Payout file | payout record | `GET /internal/settlements/{id}/payout-file` | yes |

Determinism rules:

- rows sorted by account name or reference
- no timestamps or random identifiers in the body
- amounts are integer minor units
- the attestation includes a SHA-256 digest and an HMAC signature

The same input therefore produces byte-identical output, which is what makes a report
verifiable by a third party.

## Attestation format

```
runId=<uuid>
internal=<count>
psp=<count>
bank=<count>
breaks=<count>
open=<count>
resolved=<count>
digest=<sha256 of sorted reference,type,status lines>
signature=<hmac-sha256 of the lines above>
```

The signing key comes from `report.signing-key`. The default is development-only and
must be overridden in any real deployment.

## Retention policy

| Data | Retention | Rationale |
|---|---|---|
| Ledger lines | 8 years | accounting and audit |
| Settlement records | 8 years | tax and dispute defence |
| Recon runs and breaks | 8 years | regulatory attestation |
| Webhook events and DLQ | 180 days | operational debugging |
| Application logs | 30 days | incident response |
| Reports | 8 years | reproducible from the ledger |

Configured intent: `report.retention-days: 2920` (8 years). No scheduled purge is
implemented; retention is a documented policy, not an enforced job.

## Known limits

- Reports read the payment ledger only. Settlement and payout records live in the
  operations database and are reported separately.
- No accounting period close or trial balance yet.
- Reports are generated on demand; no signed daily drop to object storage.
