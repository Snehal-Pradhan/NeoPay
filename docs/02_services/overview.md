# Service map

Seven services, four bounded contexts. The gateway owns authentication; the platform services own routing and configuration.

## Boundaries

| Service | Bounded context | Owns | Does not own |
|---|---|---|---|
| merchant | Merchant identity and commercial profile | Merchants, users, API keys, KYC state, limits, webhook configuration | Card data, payments |
| payment | Payment lifecycle and ledger | Orders, payments, refunds, disputes, ledger, outbox, inbound PSP events | Settlement, merchant bank details |
| operations | Money movement after capture | Settlement, payouts, reconciliation, merchant webhook delivery | Payment state transitions |
| vault | Card data | Tokens, encrypted PANs | Any payment decision |
| api-gateway | Edge policy | Routing, auth, throttling, idempotency filter | Domain logic |
| discovery | Registry | Service locations | Configuration |
| config | Configuration | Per-service, per-profile YAML | Secrets |

## Communication map

| From | To | Protocol | When |
|---|---|---|---|
| gateway | all services | HTTP, path prefix | Every request |
| payment | merchant | HTTP | Risk limits and merchant status at initiation |
| payment | vault | HTTP | Card charges only |
| payment | processor boundary | HTTP | Charge and capture |
| processor | payment | signed HTTP | Asynchronous result |
| operations | payment | HTTP | Settlement candidates, ledger posting, mark settled |
| operations | merchant | HTTP | Bank details, active merchant list |
| operations | merchant backend | signed HTTP | Domain event delivery |
| payment, operations | Kafka | produce | Outbox relay |
| operations | Kafka | consume | Webhook fan-out |

## Request paths

| Path prefix | Service | Authentication |
|---|---|---|
| `/v1/auth/**` | merchant | Public |
| `/v1/merchants/**` | merchant | JWT |
| `/v1/orders/**`, `/v1/payments/**` | payment | API key |
| `/v1/vault/**` | vault | API key or JWT |
| `/webhook/stripe` | payment | `Stripe-Signature` |
| `/webhook/**` | operations | Public route; merchant signature on delivery |
| `/actuator/**` | all | Public inside the cluster |

Routes: `config-service/src/main/resources/config/api-gateway-service.yml`.

## Failure isolation

| Failure | Blast radius | Mitigation |
|---|---|---|
| payment down | No new payments; settlement stalls | Gateway returns 5xx; outbox unaffected |
| operations down | No settlement or merchant webhooks | Payment writes continue; outbox grows |
| vault down | Card charges fail; UPI and netbanking continue | Circuit breaker on vault client |
| merchant down | Risk check fails open; settlement bank details unavailable | `RiskEnforcer` counts the failure and allows the payment |
| Redis down | Throttling weakens; jobs pause | Idempotency still enforced by database uniqueness |
| Kafka down | Webhook delivery delayed | Outbox retains events |

## Service documents

| Service | Document |
|---|---|
| api-gateway | [api_gateway.md](api_gateway.md) |
| merchant auth module | [auth_service.md](auth_service.md) |
| merchant | [merchant_service.md](merchant_service.md) |
| payment | [payment_service.md](payment_service.md) |
| operations | [operations_service.md](operations_service.md) |
| vault | [vault_service.md](vault_service.md) |
| config | [config_service.md](config_service.md) |
| discovery | [discovery_service.md](discovery_service.md) |
