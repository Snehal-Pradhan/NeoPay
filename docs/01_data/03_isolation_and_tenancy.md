# Isolation and tenancy

NeoPay isolates tenants by row ownership inside a shared schema per service. It does not use row-level security, schema-per-tenant, or database-per-tenant.

## Model

| Layer | Isolation mechanism | Enforced by |
|---|---|---|
| Service | Separate database per service | Deployment and credentials |
| Tenant | `merchant_id` column on owned rows | Repository queries and request context |
| Request | `MerchantContext` request-scoped bean | `MerchantContextFilter` from the gateway headers |
| Card data | Separate service and database | `vault-service` boundary, see [PCI scope](../PCI_SCOPE.md) |

## Where tenant scoping is enforced

| Operation | Scoping |
|---|---|
| Order create and cancel | `findByIdAndMerchantId` |
| Payment initiate, capture | `findByIdAndMerchantIdForUpdate` |
| Refund | Payment must belong to the merchant |
| Dispute open, evidence, represent, resolve | `requireOpenDispute` compares `dispute.merchantId` |
| Settlement | Merchant id from the scheduler or admin call; payments fetched by merchant |
| Webhook configuration | Merchant id from the JWT |

A foreign dispute is reported as `404`, not `403`, so the API does not disclose existence.

## Why not row-level security

| Option | Benefit | Cost in this repository |
|---|---|---|
| Application scoping (used) | Visible in tests; works with H2 and Postgres | One missed repository method leaks rows |
| Postgres RLS | Enforced by the database | Requires a session variable per request and a connection-pool hook; H2 tests would bypass it |
| Schema per tenant | Strong isolation | Migration fan-out across thousands of schemas |
| Database per tenant | Strongest isolation | Connection and cost per merchant |

## Evidence that scoping holds

| Test | Asserts |
|---|---|
| `PaymentIdempotencyConcurrencyTest` | same key from one merchant yields one row |
| `DisputeEvidenceLifecycleTest.foreignMerchantCannotSubmitEvidence` | another merchant cannot touch a dispute |
| `ArchitectureTest.controllers_must_not_access_repositories` | tenant filtering cannot be skipped in a controller |

## Known gaps

| Gap | Risk |
|---|---|
| No database-level tenant guard | A new repository method without `merchantId` can read across tenants |
| Internal endpoints on `merchant-service` are unauthenticated | Cluster network is the only control |
| Admin settlement calls are not merchant-scoped by design | Operator action |

Related: [ER diagram](01_er_diagram_and_schema.md), [PCI scope](../PCI_SCOPE.md).
