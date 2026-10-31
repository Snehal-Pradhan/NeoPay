# System context

NeoPay is a payments platform for Indian merchants. It accepts an order, authorizes and captures a payment through a processor boundary, keeps a double-entry ledger, settles captured volume to a merchant bank account, and delivers signed webhooks to the merchant.

## Actors

| Actor | Interaction | Entry point |
|---|---|---|
| Merchant backend | Creates orders, payments, refunds, disputes; reads reports | `api-gateway-service:8080` with an API key |
| Merchant operations | Tokenizes cards, reviews disputes, downloads statements | Same gateway with a JWT |
| Card and UPI processor | Receives charge calls, posts signed results back | Outbound HTTPS; inbound `POST /webhook/stripe` |
| Settlement bank | Receives the payout file; posts acknowledgment | Outbound file; callback simulator locally |
| Platform operator | Runs schedules, inspects the ledger, replays failures | Internal endpoints and SQL |

## Trust boundaries

| Boundary | Control |
|---|---|
| Merchant to gateway | API key plus secret, HTTP Basic; JWT for dashboard routes |
| Processor to payment service | `Stripe-Signature` HMAC over `<timestamp>.<body>`, 300 s tolerance |
| Service to service | Cluster network plus per-route authorization at the gateway |
| Card data | Only `vault-service` sees a PAN |

Source: `api-gateway-service/src/main/java/com/snehal/neopay/gateway/security/`, `payment-service/src/main/java/com/snehal/neopay/payment/webhook/StripeWebhookVerifier.java`.

## Data ownership

Each service owns one database and no service reads another's tables.

| Service | Database | Owns |
|---|---|---|
| merchant | `neopay-merchant-db` | merchants, users, API keys, customers, webhook configuration |
| payment | `neopay-payment-db` | orders, payments, refunds, disputes, ledger, outbox, webhook events |
| operations | `neopay-operations-db` | settlements, payouts, reconciliation, merchant webhook delivery |
| vault | `neopay-vault-db` | card tokens, encrypted PANs |

## Out of scope

NeoPay is a demonstration system. It runs no production traffic, holds no real money, and integrates with no live PSP. The processor boundary is exercised against a Stripe-shaped sandbox adapter and WireMock contracts, not against Stripe production.

Related: [container architecture](02_container_architecture.md), [service map](../02_services/overview.md), [PCI scope](../PCI_SCOPE.md).
