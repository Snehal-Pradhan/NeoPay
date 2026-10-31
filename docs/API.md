# API Contract

## Machine-readable specification

Each service exposes OpenAPI 3.1 at:

```
GET /v3/api-docs
GET /swagger-ui.html
```

Committed snapshot: `docs/openapi/payment-service.json`.

Regenerate:

```bash
mvn -q -f payment-service/pom.xml test -Dtest=OpenApiContractTest -Dopenapi.write=true
```

## Versioning

| Rule | Detail |
|---|---|
| Scheme | Path-based major version: `/v1/...` |
| Additive change | New optional field or endpoint  -  no version bump |
| Breaking change | Field removal, type change, required-field addition  -  new `/v2` |
| Deprecation | `Deprecation` and `Sunset` response headers on the deprecated operation |
| Overlap | Old and new major versions run concurrently for at least one release cycle |

## Error contract

All errors use `application/problem+json` per RFC 9457.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Payment already captured",
  "instance": "/v1/payments/8f14e45f",
  "errorCode": "INVALID_STATE_TRANSITION",
  "timestamp": "2026-10-25T14:32:11.482Z"
}
```

| Field | Status |
|---|---|
| `type`, `title`, `status`, `detail` | Standard RFC 9457 |
| `instance` | Request URI |
| `errorCode` | Extension, stable machine-readable code |
| `timestamp` | Extension, ISO-8601 UTC |
| `fieldErrors` | Extension, present only on validation failures |

`errorCode` is the only field clients may branch on. `detail` is human-readable and
may change without notice.

## Pagination

List endpoints accept `page`, `size`, `sort` and return:

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true
}
```

Defaults: `size=20`, `sort=createdAt,desc`. Maximum page size: 100.

## Idempotency

| Property | Value |
|---|---|
| Header | `X-Idempotency-Key` |
| Scope | `POST /v1/payments`, `POST /v1/payments/{id}/refunds` |
| Key space | `(merchant_id, idempotency_key)` |
| Storage | Unique database index plus Redis in-progress marker |
| Replay | Returns the original `PaymentResponse`, does not call the gateway |
| Conflict | Same key, different payload → `409 IDEMPOTENCY_CONFLICT` |
| Lifetime | Valid for the lifetime of the stored payment |
| Requirement | Callers must use a stable key per logical payment attempt |
| Capture | Idempotent by state: a captured payment returns the stored response, no header needed |

## Pagination and idempotency coverage

| Endpoint | Idempotent | Paginated |
|---|---|---|
| `POST /v1/payments` | yes |  -  |
| `POST /v1/payments/{id}/refunds` | yes |  -  |
| `GET /v1/payments/{id}/refunds` |  -  | no |
| `POST /v1/orders` | no |  -  |
| `POST /v1/merchants/api-keys` | no |  -  |
| `GET /v1/merchants/api-keys` |  -  | yes |
| `GET /v1/merchants/webhooks` |  -  | yes |
| `POST /v1/merchants/webhooks` | no |  -  |

## Breaking-change checklist

1. Add the operation to `/v2` with the new shape.
2. Keep `/v1` behavior unchanged.
3. Add `Deprecation` and `Sunset` headers to `/v1`.
4. Regenerate and commit `docs/openapi/*.json`.
5. Publish the migration note in `CHANGELOG.md`.
