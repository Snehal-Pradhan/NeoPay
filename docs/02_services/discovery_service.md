# discovery-service

Eureka registry. Services register on startup and resolve each other by service name.

![discovery flow](../assets/service_discovery_flow.png)

## Scope

| In scope | Out of scope |
|---|---|
| Service registration | Load-balancer configuration |
| Registry reads for `lb://` routes | Health checking of business endpoints |
| Lease renewal | Cross-cluster federation |

## Behaviour

| Setting | Value |
|---|---|
| Port | 8761 |
| Registry | In-memory, not persisted |
| Self-registration | Disabled for the server |
| Client registration | Every service registers on startup |

Clients point at `EUREKA_URL`, default `http://localhost:8761/eureka/`.

## Failure modes

| Condition | Result |
|---|---|
| Registry restart | In-memory registry empties; clients re-register within one lease cycle |
| Stale instance | Gateway routes to a dead address until the lease expires; the request fails |
| Discovery down at startup | A client retries; a gateway route uses `lb://` and fails until registration succeeds |

For a local stack without discovery, `k8s` profile sets direct service URLs and disables Eureka.

## Diagram script

```python
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server

with Diagram("discovery-service flow", show=False, direction="LR",
             filename="./docs/assets/service_discovery_flow", outformat="png"):
    with Cluster("discovery-service"):
        registry = Server("Eureka server\nregistry")
    with Cluster("Clients"):
        gateway = Server("api-gateway")
        merchant = Server("merchant")
        payment = Server("payment")
        operations = Server("operations")
        vault = Server("vault")

    gateway >> Edge(label="register / fetch") >> registry
    merchant >> registry
    payment >> registry
    operations >> registry
    vault >> registry
```

Related: [container architecture](../00_architecture/02_container_architecture.md).
