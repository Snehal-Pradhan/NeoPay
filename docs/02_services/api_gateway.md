# api-gateway-service

The only public entry point. It authenticates merchants, throttles them, applies the idempotency filter, and routes to one of four services.

<p align="center"><img src="../assets/service_api_gateway_flow.png" width="60%" alt="api-gateway flow"></p>

## Scope

| In scope | Out of scope |
|---|---|
| API key and JWT validation | Issuing tokens |
| Per-key rate limiting | Domain-level idempotency |
| Route selection | Response transformation |
| Correlation and trace propagation | Retries of upstream calls |

Entry point: `api-gateway-service/src/main/java/com/snehal/neopay/gateway/`.

## Request pipeline

| Order | Component | Behaviour |
|---:|---|---|
| 1 | `GatewayAuthFilter` | Skips public routes; otherwise validates the Authorization header |
| 2 | `FixedWindowRateLimiter` | Counts requests per key per minute in Redis |
| 3 | `IdempotencyFilter` | Present only when `X-Idempotency-Key` is sent; stores the response for replay |
| 4 | Route table | Path prefix to `lb://<service>` |

## API

The gateway exposes no API of its own. It forwards:

| Path | Upstream |
|---|---|
| `/v1/auth/**`, `/v1/merchants/**` | merchant |
| `/v1/orders/**`, `/v1/payments/**` | payment |
| `/v1/vault/**` | vault |
| `/webhook/stripe` | payment |
| `/webhook/**` | operations |

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `app.security.public-routes` | `/v1/auth/**`, `/webhook/**`, `/actuator/**` | Paths that bypass authentication |
| `app.security.trust-inbound-headers` | `false` | Whether identity headers from outside are trusted |
| `app.rate-limit.use-case.api-key.requests-per-minute` | 200 | Fixed-window limit per key |

Source: `config-service/src/main/resources/config/api-gateway-service.yml`.

## Failure modes

| Condition | Response | Notes |
|---|---|---|
| Missing or invalid credentials | `401 UNAUTHORIZED` | Detail names the header, not the key |
| Limit exceeded | `429 RATE_LIMIT_EXCEEDED` | Fixed window; bursts inside one window can exceed the per-minute average |
| Redis unavailable | Request passes through | Limiter and replay cache fail open |
| Upstream unavailable | `503` from the route | No retry |
| Duplicate key in progress | `409 IDEMPOTENCY_CONFLICT` | 120 s placeholder TTL |

The 200-per-minute limit produced 0.11% `429` responses at 60 flows/s with 60 keys. Measurement: [PERFORMANCE](../PERFORMANCE.md).

## Diagram script

```python
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.client import Users
from diagrams.onprem.compute import Server
from diagrams.onprem.inmemory import Redis

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_api_gateway_flow", outformat="png"):
    client = node(Users, "Merchants")
    auth = node(Server, "auth filter")
    limit = node(Server, "rate limit")
    idem = node(Server, "idempotency")
    router = node(Server, "router")
    cache = node(Redis, "Redis")
    payment = node(Server, "payment")
    merchant = node(Server, "merchant")
    operations = node(Server, "operations")
    vault = node(Server, "vault")

    client >> Edge(label="API key") >> auth >> limit >> idem >> router
    auth >> Edge(label="lookup", style="dashed") >> cache
    limit >> Edge(label="INCR", style="dashed") >> cache
    router >> payment
    router >> merchant
    router >> operations
    router >> vault
```

Related: [service map](overview.md), [idempotency](../03_patterns/03_idempotency.md), [resilience](../03_patterns/04_resilience_circuit_breakers.md).
