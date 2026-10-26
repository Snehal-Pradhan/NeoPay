# Features — Microservices Edition

In-depth inventory of the distributed NeoPay-style platform.

- **Architecture:** API gateway + discovery + config server + 4 business services + shared library
- **Runtime:** Java 21, Spring Boot 4.1.0, Spring Cloud 2025.1.2
- **Data:** one PostgreSQL server, four service-owned databases
- **Async:** Kafka transactional outbox + webhook fan-out
- **Coordination:** Redis for caches, rate limits, idempotency, retries, ShedLock
- **Observability:** Zipkin tracing; K8s/Prometheus/Grafana manifests included

---

## 1. Service catalogue

| Service | Port | Owns | Depends on |
|---|---:|---|---|
| `api-gateway-service` | 8080 | Auth, routing, rate limiting | merchant (API-key lookup), Eureka |
| `merchant-service` | 9010 | Merchants, users, customers, API keys, webhook configs | merchant DB, Redis |
| `payment-service` | 9020 | Orders, payments, refunds (entity), outbox, simulator | payment DB, merchant, vault, Kafka, Redis |
| `vault-service` | 9040 | Cards, tokens, PAN encryption | vault DB, Redis |
| `operations-service` | 9030 | Settlements, webhook delivery, DLQ, outbox | operations DB, merchant, payment, Kafka, Redis |
| `config-service` | 8888 | Central configuration | native config repo |
| `discovery-service` | 8761 | Eureka registry | — |
| `common-lib` | — | Shared DTOs, entities, enums, auto-configuration | — |

---

## 2. API gateway

### Authentication

- Public routes: `/v1/auth/**`, `/webhook/**`, `/actuator/**`.
- API-key routes: HTTP Basic (`keyId:keySecret`) for order/payment/vault traffic.
- JWT routes: Bearer token for merchant dashboard traffic.
- The gateway resolves API keys through `merchant-service`'s internal endpoint, caches entries in Redis for 5 minutes, and caches successful BCrypt verification for 30 seconds.
- On success it injects:
  - `X-Merchant-Id`
  - `X-Key-Id`
  - `X-Environment`
  - `X-User-Role`
- Downstream services trust these headers through `MerchantContextFilter`.

### Routing

| Route | Upstream |
|---|---|
| `/v1/auth/**`, `/v1/merchants/**` | `merchant-service` |
| `/v1/orders/**`, `/v1/payments/**` | `payment-service` |
| `/v1/vault/**` | `vault-service` |
| `/webhook/**` | `operations-service` |

Routes use Eureka load balancing (`lb://...`) and can be overridden with `*_SERVICE_URI` variables.

### Errors and throttling

- Missing/invalid credentials → `401 UNAUTHORIZED`.
- Rate limit → `429 RATE_LIMIT_EXCEEDED` with `Retry-After`.
- Errors are returned as `{errorCode, errorDescription}`.
- Rate limiting is Redis-backed; configured at 200 API-key requests/minute by default.

---

## 3. Merchant service

### Signup and login

- BCrypt password hashing.
- New merchants start as `PENDING_KYC`.
- Login returns a JWT with email, merchant ID, role, and 100-minute expiry.
- JWT secret is shared with the gateway.

### API keys

- Create/list/revoke/rotate endpoints.
- Raw secret returned only on create/rotate.
- BCrypt hash stored at rest.
- Redis metadata cache (5 minutes).
- Revocation sets `enabled=false` and evicts the cache.
- Rotation keeps the previous secret valid for a 24-hour grace period.
- Internal lookup endpoint returns a cache-ready `ApiKeyCacheEntry`.

### Customers

- Find-or-create by merchant + email.
- Internal endpoint used by payment service during order creation.

### Webhook configuration

- Create/list/get/update/delete merchant webhook targets.
- Random webhook secret generated at creation and returned once.
- Secret encrypted at rest with a dedicated AES key.
- `eventTypes` accepts comma-separated event names or `ALL`.
- Internal lookup endpoint returns active targets for a merchant/event.

### Internal endpoints

| Method | Path | Consumer |
|---|---|---|
| GET | `/internal/api-keys/{keyId}` | gateway |
| POST | `/internal/customers/find-or-create` | payment |
| GET | `/internal/merchants/{id}/webhook-targets` | operations |
| GET | `/internal/merchants/active-ids` | operations |
| GET | `/internal/merchants/{id}/settlement-bank-details` | operations |

---

## 4. Payment service

### Orders

- Create order with amount, receipt, notes, expiry, optional customer.
- Duplicate merchant + receipt → `409`.
- Default expiry 30 minutes.
- Writes an `ORDER_CREATED` outbox event in the same transaction.
- Service supports get/cancel/list payments; only create is exposed over HTTP.

### Payment initiation

- `POST /v1/payments` with optional `X-Idempotency-Key`.
- Idempotency is enforced by a unique index on `(merchant_id, idempotency_key)`.
- Existing attempts are replayed; concurrent duplicate inserts are recovered.
- Two-phase transaction:
  1. record the attempt and transition to `AUTHORIZING`,
  2. call the gateway outside the transaction,
  3. apply the result or compensate a failure in a new transaction.
- Compensation produces `PAYMENT_AUTHORIZATION_COMPENSATED` and a `FAILED` payment.

### Methods

| Method | Behaviour |
|---|---|
| CARD | Calls vault over Feign; vault detokenizes and charges |
| UPI | Requires `vpa`; `fail@okaxis` simulates rejection |
| NETBANKING | Requires `bank`; `BANK_CODE_FAIL` simulates rejection |
| WALLET | Not implemented |

### Capture and callbacks

- Capture an authorized payment.
- Bank callback simulator runs every 5 seconds and resolves `AUTHORIZING` payments.
- Success → `AUTHORIZED` → `CAPTURING` → `CAPTURED`; order becomes `PAID`.
- Decline → `FAILED`.
- Chaos modes: `NORMAL`, `SLOW`, `FAILURE`, `SUCCESS`, `TIMEOUT`.
- Per-method delay and success-rate configuration.

### State machine

`CREATED → AUTHORIZING → AUTHORIZED → CAPTURING → CAPTURED → SETTLED`

Also supports failure, cancellation, expiry, partial refund, and full refund states. Every transition is written to `payment_transition_log`.

### Outbox

- `OutboxEventPublisher` writes in the business transaction.
- `OutboxPoller` publishes to Kafka every 5 seconds.
- Rows move `PENDING → PUBLISHED` or `FAILED`.
- Kafka key is the merchant ID when available.

### Internal settlement API

| Method | Path | Consumer |
|---|---|---|
| GET | `/internal/payments/unsettled-captured` | operations |
| POST | `/internal/payments/mark-settled` | operations |

`mark-settled` transitions payments to `SETTLED` and sets `settledAt`.

---

## 5. Vault service

- `POST /v1/vault/tokenize` validates PAN (Luhn + length), CVV, month, and future expiry.
- Detects Visa, Mastercard, Amex, RuPay by BIN.
- Generates a random 32-byte DEK per card.
- Encrypts PAN with AES-GCM.
- Wraps the DEK with the master key.
- Stores only BIN, last four, brand, expiry, cardholder name, encrypted PAN/DEK.
- Returns a `tok_...` token.
- Internal `/internal/vault/charge`:
  - looks up an active token,
  - decrypts the DEK/PAN in memory,
  - charges the card processor,
  - wipes plaintext PAN bytes.
- Card declines:
  - `4000000000000002` → `CARD_DECLINED`
  - `4000000000000069` → `CARD_EXPIRED`
- The Feign response uses a concrete `VaultChargeResponse` with a `type` discriminator for reliable serialization.

---

## 6. Operations service

### Settlements

- Nightly cron at 23:00, locked with ShedLock.
- Loads active merchant IDs from merchant service.
- For each merchant:
  - fetches unsettled captured payments from payment service,
  - computes gross, 2% fee, 18% GST on the fee, and net,
  - creates settlement + payment links,
  - initiates a simulated bank transfer,
  - stores `TRANSFER_PENDING`.
- Bank callback simulator resolves pending transfers every 5 seconds.
- Success:
  - settlement → `PROCESSED`,
  - linked payments marked `SETTLED`,
  - `SETTLEMENT_PROCESSED` event published.
- Failure:
  - settlement → `FAILED`,
  - `SETTLEMENT_FAILED` event published.
- Feign calls are protected by Resilience4j circuit breakers and retries.

### Webhooks

- Consumes `payments.events`, `orders.events`, `refund.events`, `settlements.events` with manual acknowledgements.
- Loads targets from merchant service.
- Signs `{event, payload}` with HMAC-SHA256.
- Stores `PENDING` webhook events and enqueues due IDs in Redis.
- Delivery scheduler:
  - polls due IDs every second,
  - dispatches on virtual threads,
  - retries non-2xx/transport failures with `[1m, 5m, 30m, 2h, 8h, 24h]`,
  - marks `DEAD` and records a DLQ row after 7 attempts.
- Reconciliation job requeues pending DB rows every 10 seconds.
- Dummy receiver at `/webhook/success` returns 204.

### Outbox

Operations owns its own outbox and poller, so settlement events do not share payment's database.

---

## 7. Shared library (`common-lib`)

- `BaseEntity` auditing: created/updated timestamps and actors.
- `Money` value object with currency guard.
- Domain enums: payment/order/refund/settlement statuses, events, methods, environments, roles.
- Domain exceptions and a complete `GlobalExceptionHandler` (400/401/404/409/429/500).
- `MerchantContext` request-scoped identity.
- `MerchantContextFilter` reads gateway-injected headers.
- Idempotency filter/store backed by Redis.
- Four rate-limiter strategies: fixed, sliding, sliding-Lua, token bucket.
- API-key cache.
- Kafka topic properties and trusted package configuration.
- HMAC signer and randomizer.
- Zipkin sender configuration.
- Spring Boot auto-configuration entries for shared security, web filters, Kafka, Redis/resilience, auditing, and exceptions.

---

## 8. Data ownership

| Database | Tables |
|---|---|
| `neopay-merchant-db` | `merchant`, `app_user`, `customer`, `api_key`, `merchant_webhook_config` |
| `neopay-payment-db` | `order_record`, `payment`, `payment_transition_log`, `refund`, `outbox_event` |
| `neopay-vault-db` | `vault_card`, `card_token` |
| `neopay-operations-db` | `settlement`, `settlement_payment`, `webhook_event`, `dlq_event`, `outbox_event` |

No service reads another service's tables directly. Cross-service reads go through internal REST endpoints.

---

## 9. Event and scheduling inventory

### Kafka topics

| Event | Topic |
|---|---|
| Payment events | `payments.events` |
| Order events | `orders.events` |
| Refund events (producer not implemented) | `refund.events` |
| Settlement events | `settlement.events` |

Envelope:

```json
{
  "eventType": "PAYMENT_CREATED",
  "aggregateType": "PAYMENT",
  "aggregateId": "<uuid>",
  "data": { "...": "..." }
}
```

### Scheduled jobs

| Schedule | Service | Job | ShedLock |
|---|---|---|---|
| every 5s | payment | outbox poller | yes |
| every 5s | payment | bank callback simulator | yes |
| every 5s | operations | bank settlement simulator | yes |
| every 1s | operations | webhook poll/deliver | yes |
| every 10s | operations | webhook reconcile | yes |
| 23:00 | operations | settlement engine | yes |

---

## 10. Configuration

| Area | Source |
|---|---|
| Service ports, DB URLs, JWT keys, resilience, routes | `config-service/src/main/resources/config/*.yml` |
| Local infra overrides | environment variables from `scripts/run-local.sh` |
| Shared defaults (Redis/Kafka/Eureka/topics) | `config/application.yml` |
| Kubernetes | `k8s/` manifests + sanitized `k8s-secrets.env` |
| Observability | `observability/docker-compose.yml` |

Key environment variables:

- `JWT_SECRET`
- `VAULT_MASTER_KEY`
- `WEBHOOK_SECRET_ENCRYPTION_KEY`
- `MERCHANT_DB_URL`, `PAYMENT_DB_URL`, `OPERATIONS_DB_URL`, `VAULT_DB_URL`
- `DB_USER`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`
- `KAFKA_BROKERS`
- `EUREKA_URL`
- `CONFIG_SERVER_URL`

---

## 11. Testing and live verification

### Automated

- One Spring context test per service (7 total).
- Test profiles use H2, disable config/discovery, and disable Kafka listeners.
- `common-lib` is built and installed first.

### Live verified

- All 7 services started with Eureka + Config Server.
- Signup, login, API key, order, tokenize, card payment through the gateway.
- Payment → vault Feign call returned a valid pending result.
- Simulator auto-captured the payment and marked the order `PAID`.
- Payment idempotency returned the same payment with one DB row.
- Outbox → Kafka → operations consumer → webhook delivery to the merchant target, status `DELIVERED`, HTTP 200.
- ShedLock keys present in Redis.
- Gateway 401 on revoked/invalid credentials.
- Infrastructure torn down with `docker compose down -v`.

### Commands

```bash
docker compose up -d
./build-all.sh
./scripts/run-local.sh
./scripts/stop-local.sh
docker compose down -v
```

---

## 12. Security model

- Public traffic terminates at the gateway.
- Gateway performs authentication and injects trusted identity headers.
- Downstream services have no end-user auth chain; they rely on network isolation.
- Internal endpoints must never be exposed publicly.
- JWT/vault/webhook keys are env-overridable; local defaults are development-only.
- Reference hardcoded GitHub PAT and real-looking secrets were removed.
- API-key secrets are BCrypt-hashed; webhook secrets are AES-encrypted at rest.
- Jib publishing is disabled by default (`jib.skip=true`) and uses neutral image names.

---

## 13. Known gaps

- Refund domain: entity/enums only; no service, API, processor, or event producer.
- Wallet payment method has no adapter/processor.
- Order/payment read APIs are incomplete (only create/initiate/capture exposed).
- No refresh tokens, logout, password reset, or multi-user APIs.
- No admin replay for `FAILED` outbox rows or `DEAD` webhooks.
- No automated Testcontainers end-to-end suite.
- K8s manifests are provided but not continuously exercised.
- No pagination on list endpoints.
- Internal service endpoints are unauthenticated (network-trust model).
