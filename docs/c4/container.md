# C4  -  Containers

```mermaid
flowchart TB
    client[Merchant / Customer]

    subgraph edge[Edge]
        gw[api-gateway-service<br/>auth, rate limit, routing]
    end

    subgraph services[Business services]
        merchant[merchant-service<br/>accounts, keys, webhooks]
        payment[payment-service<br/>orders, payments, outbox]
        vault[vault-service<br/>tokenization, encryption]
        ops[operations-service<br/>settlement, webhook delivery]
    end

    subgraph platform[Platform services]
        config[config-service<br/>native config]
        discovery[discovery-service<br/>Eureka]
    end

    subgraph data[Data]
        mdb[(merchant DB)]
        pdb[(payment DB)]
        vdb[(vault DB)]
        odb[(operations DB)]
        redis[(Redis)]
        kafka[(Kafka)]
    end

    client --> gw
    gw --> merchant
    gw --> payment
    gw --> vault
    gw --> ops
    payment --> vault
    payment --> merchant
    ops --> merchant
    ops --> payment

    merchant --> mdb
    payment --> pdb
    vault --> vdb
    ops --> odb

    gw --> redis
    merchant --> redis
    payment --> redis
    ops --> redis

    payment --> kafka
    ops --> kafka
    kafka --> ops

    merchant -.-> discovery
    payment -.-> discovery
    vault -.-> discovery
    ops -.-> discovery
    gw -.-> discovery
    gw -.-> config
```

## Container responsibilities

| Container | Port | Owns | Scaling unit |
|---|---:|---|---|
| `api-gateway-service` | 8080 | Authentication, rate limiting, routing | Stateless |
| `merchant-service` | 9010 | Merchants, users, customers, API keys, webhook targets | Stateless |
| `payment-service` | 9020 | Orders, payments, outbox, processors | Stateless |
| `vault-service` | 9040 | Cards, tokens, encryption | Stateless |
| `operations-service` | 9030 | Settlements, webhook delivery, DLQ | Stateless |
| `config-service` | 8888 | Central configuration | Stateless |
| `discovery-service` | 8761 | Service registry | Stateless |

All state lives in Postgres, Redis or Kafka. No container holds authoritative state in
memory, which is the precondition for horizontal scaling.
