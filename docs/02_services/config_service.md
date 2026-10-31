# config-service

Serves per-service, per-profile YAML from the classpath. Services import it at startup with `spring.config.import`.

![config flow](../assets/service_config_flow.png)

## Scope

| In scope | Out of scope |
|---|---|
| Configuration files for seven services | Secret storage |
| Profile overlays (`k8s`, `test`) | Dynamic refresh |
| Central defaults for URLs, limits, resilience | Feature flags |

Entry point: `config-service/src/main/resources/config/`.

## Layout

| File | Owner |
|---|---|
| `config/application.yml` | Shared defaults |
| `config/merchant-service.yml` | Merchant service |
| `config/payment-service.yml` | Payment service |
| `config/operations-service.yml` | Operations service |
| `config/vault-service.yml` | Vault service |
| `config/api-gateway-service.yml` | Gateway routes, public paths, limits |

## Consumption

```yaml
spring:
  config:
    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

`optional:` means a service starts with local defaults when the config server is unreachable. `SPRING_CLOUD_CONFIG_FAIL_FAST` controls strictness in the cluster.

## Profiles

| Profile | Effect |
|---|---|
| default | Local URLs on `localhost` |
| `k8s` | Cluster DNS names, Eureka disabled for the config server itself |
| `test` | Disables config import, Flyway, and Eureka in tests |

## Failure modes

| Condition | Result |
|---|---|
| Config server slow | Retries with backoff, then starts with defaults |
| Malformed YAML | Startup fails; the file is validated by the service parsing it |
| Secret placed in YAML | Loaded as plain text; secrets belong in environment variables |

## Diagram script

```python
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.generic.storage import Storage

with Diagram("config-service flow", show=False, direction="LR",
             filename="./docs/assets/service_config_flow", outformat="png"):
    with Cluster("config-service"):
        api = Server("ConfigServer\nnative profile")
        repo = Storage("classpath:/config\nper-service yml")
    clients = Server("7 services\nspring.config.import")

    api >> repo
    clients >> Edge(label="GET /{service}/{profile}") >> api
```

Related: [ADR 0007](../adr/0007-native-config-server.md), [deployment](../04_operations/02_deployment_and_hpa.md).
