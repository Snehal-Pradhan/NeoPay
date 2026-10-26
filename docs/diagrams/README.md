# Architecture Diagrams (Eraser.io)

Diagram-as-code definitions using the 3-colour semantic palette (`blue` = caller/actor,
`gray` = NeoPay service/component, `green` = data/observability). Solid black arrows are
primary request flows; dashed grey arrows are static dependencies such as discovery and
configuration lookups.

| File | Scope |
|---|---|
| `01-platform-overview.eraser` | High-level 7-service topology and backing stores |
| `02-api-gateway-service.eraser` | Filter chain, auth handlers, header stripping, Redis rate limiter |
| `03-merchant-service.eraser` | Auth, API keys, webhook config, KYC, and risk engine |
| `04-payment-service.eraser` | Orders, payments, refunds, disputes, double-entry ledger, FX lock, outbox |
| `05-vault-service.eraser` | Envelope encryption (DEK/KEK) and card processor bulkhead |
| `06-operations-service.eraser` | Settlement engine, payouts, reconciliation, and webhook delivery |
| `07-discovery-service.eraser` | Eureka service registration and registry replication |
| `08-config-service.eraser` | Spring Cloud Config native repository and startup config pull |

`architecture-overview.svg` is the hand-authored vector version of `01-platform-overview.eraser`
and is embedded at the top of `README.md`; it needs no external hosting.
