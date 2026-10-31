# Configuration reference

Generated from `config-service/src/main/resources/config/*.yml` by
`scripts/generate-config-reference.py`. Do not edit by hand.

Values written as `${NAME:default}` resolve from the environment with `default`
when the variable is absent. A blank value means the key is optional or supplied
at runtime.

## api-gateway-service

### profile: `default`

| Key | Value |
|---|---|
| `server.port` | `8080` |
| `app.security.trust-inbound-headers` | `False` |
| `app.security.public-routes` | `/v1/auth/**, /webhook/**, /actuator/**` |
| `app.rate-limit.method` | `fixed` |
| `app.rate-limit.use-case.api-key.requests-per-minute` | `200` |
| `spring.cloud.gateway.server.webmvc.routes` | `{'id': 'merchant-service', 'uri': '${MERCHANT_SERVICE_URI:lb://merchant-service}', 'predicates': ['Path=/v1/auth/**, /v1/merchants/**']}, {'id': 'payment-service', 'uri': '${PAYMENT_SERVICE_URI:lb://payment-service}', 'predicates': ['Path=/v1/orders/**, /v1/payments/**']}, {'id': 'vault-service', 'uri': '${VAULT_SERVICE_URI:lb://vault-service}', 'predicates': ['Path=/v1/vault/**']}, {'id': 'stripe-webhook', 'uri': '${PAYMENT_SERVICE_URI:lb://payment-service}', 'predicates': ['Path=/webhook/stripe']}, {'id': 'operations-service', 'uri': '${OPERATIONS_SERVICE_URI:lb://operations-service}', 'predicates': ['Path=/webhook/**']}` |
| `jwt.secret-key` | `${JWT_SECRET:cpY8wNotFJAUlUhd2NpJrXmviYlANykc5xljWSBJjJMjT7pR/Ils9DoNm0BIZLMd}` |

### profile: `k8s`

| Key | Value |
|---|---|
| `spring.config.activate.on-profile` | `k8s` |
| `jwt.secret-key` | `${JWT_SECRET}` |

## application

### profile: `default`

| Key | Value |
|---|---|
| `spring.threads.virtual.enabled` | `True` |
| `spring.flyway.enabled` | `True` |
| `spring.flyway.baseline-on-migrate` | `True` |
| `spring.flyway.baseline-version` | `1` |
| `spring.flyway.validate-on-migrate` | `True` |
| `spring.jpa.hibernate.ddl-auto` | `validate` |
| `spring.jpa.show-sql` | `False` |
| `spring.jpa.open-in-view` | `False` |
| `spring.jpa.properties.hibernate.dialect` | `org.hibernate.dialect.PostgreSQLDialect` |
| `spring.datasource.hikari.maximum-pool-size` | `20` |
| `spring.datasource.hikari.minimum-idle` | `5` |
| `spring.datasource.hikari.connection-timeout` | `3000` |
| `spring.datasource.hikari.idle-timeout` | `600000` |
| `spring.datasource.hikari.max-lifetime` | `1800000` |
| `spring.datasource.username` | `${DB_USER:neopay}` |
| `spring.datasource.password` | `${DB_PASSWORD:password}` |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` |
| `spring.data.redis.host` | `${REDIS_HOST:localhost}` |
| `spring.data.redis.port` | `${REDIS_PORT:6379}` |
| `spring.kafka.listener.ack-mode` | `manual` |
| `spring.kafka.bootstrap-servers` | `${KAFKA_BROKERS:localhost:29092}` |
| `spring.kafka.producer.key-serializer` | `org.apache.kafka.common.serialization.StringSerializer` |
| `spring.kafka.producer.value-serializer` | `org.springframework.kafka.support.serializer.JsonSerializer` |
| `spring.kafka.consumer.group-id` | `neopay-clone-group` |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` |
| `spring.kafka.consumer.key-deserializer` | `org.apache.kafka.common.serialization.StringDeserializer` |
| `spring.kafka.consumer.value-deserializer` | `org.springframework.kafka.support.serializer.JsonDeserializer` |
| `spring.kafka.consumer.properties.spring.json.trusted.packages` | `com.snehal.neopay.*` |
| `eureka.client.serviceUrl.defaultZone` | `${EUREKA_URL:http://localhost:8761/eureka/}` |
| `eureka.instance.prefer-ip-address` | `True` |
| `eureka.instance.hostname` | `${EUREKA_HOSTNAME:localhost}` |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` |
| `management.tracing.sampling.probability` | `0.1` |
| `management.zipkin.tracing.endpoint` | `http://localhost:9411/api/v2/spans` |
| `management.metrics.tags.application` | `${spring.application.name}` |
| `feign.httpclient.hc5.enabled` | `True` |
| `feign.client.config.default.connectTimeout` | `5000` |
| `feign.client.config.default.readTimeout` | `5000` |
| `logging.level.org.apache.kafka.clients` | `WARN` |
| `logging.level.org.apache.kafka.common` | `WARN` |
| `app.kafka.topics.payment` | `payments.events` |
| `app.kafka.topics.order` | `orders.events` |
| `app.kafka.topics.refund` | `refund.events` |
| `app.kafka.topics.settlement` | `settlement.events` |

### profile: `k8s`

| Key | Value |
|---|---|
| `spring.config.activate.on-profile` | `k8s` |
| `spring.data.redis.host` | `redis.neopay-core.svc.cluster.local` |
| `spring.data.redis.port` | `6379` |
| `spring.kafka.bootstrap-servers` | `kafka.neopay-core.svc.cluster.local:9092` |
| `spring.cloud.discovery.enabled` | `False` |
| `spring.task.scheduling.pool.size` | `4` |
| `eureka.client.enabled` | `False` |
| `eureka.client.register-with-eureka` | `False` |
| `eureka.client.fetch-registry` | `False` |
| `management.zipkin.tracing.endpoint` | `http://zipkin.neopay-core.svc.cluster.local:9411/api/v2/spans` |
| `MERCHANT_SERVICE_URI` | `http://merchant-service` |
| `PAYMENT_SERVICE_URI` | `http://payment-service` |
| `VAULT_SERVICE_URI` | `http://vault-service` |
| `OPERATIONS_SERVICE_URI` | `http://operations-service` |
| `risk.enforcement-enabled` | `${RISK_ENFORCEMENT_ENABLED:false}` |

## merchant-service

### profile: `default`

| Key | Value |
|---|---|
| `server.port` | `9010` |
| `spring.datasource.url` | `${MERCHANT_DB_URL:jdbc:postgresql://localhost:5432/neopay-merchant-db}` |
| `jwt.secret-key` | `${JWT_SECRET:cpY8wNotFJAUlUhd2NpJrXmviYlANykc5xljWSBJjJMjT7pR/Ils9DoNm0BIZLMd}` |
| `webhook.secret-encryption-key` | `${WEBHOOK_SECRET_ENCRYPTION_KEY:j66s+yPHRWFtCMBtzfqR5/sH6dBIyXcaGivTiSwktjg=}` |

### profile: `k8s`

| Key | Value |
|---|---|
| `spring.config.activate.on-profile` | `k8s` |
| `spring.datasource.url` | `jdbc:postgresql://postgres.neopay-core.svc.cluster.local:5432/neopay-merchant-db` |
| `spring.datasource.username` | `merchant_user` |
| `spring.datasource.password` | `${MERCHANT_DB_PASSWORD}` |
| `jwt.secret-key` | `${JWT_SECRET}` |
| `webhook.secret-encryption-key` | `${WEBHOOK_SECRET_ENCRYPTION_KEY}` |

## operations-service

### profile: `default`

| Key | Value |
|---|---|
| `server.port` | `9030` |
| `spring.datasource.url` | `${OPERATIONS_DB_URL:jdbc:postgresql://localhost:5432/neopay-operations-db}` |
| `spring.cloud.openfeign.client.config.merchant-service.connectTimeout` | `2000` |
| `spring.cloud.openfeign.client.config.merchant-service.readTimeout` | `3000` |
| `spring.cloud.openfeign.client.config.payment-service.connectTimeout` | `2000` |
| `spring.cloud.openfeign.client.config.payment-service.readTimeout` | `5000` |
| `resilience4j.circuitbreaker.instances.merchant-service.sliding-window-size` | `10` |
| `resilience4j.circuitbreaker.instances.merchant-service.failure-rate-threshold` | `50` |
| `resilience4j.circuitbreaker.instances.merchant-service.wait-duration-in-open-state` | `10s` |
| `resilience4j.circuitbreaker.instances.merchant-service.permitted-number-of-calls-in-half-open-state` | `3` |
| `resilience4j.circuitbreaker.instances.payment-service.sliding-window-size` | `10` |
| `resilience4j.circuitbreaker.instances.payment-service.failure-rate-threshold` | `50` |
| `resilience4j.circuitbreaker.instances.payment-service.wait-duration-in-open-state` | `10s` |
| `resilience4j.circuitbreaker.instances.payment-service.permitted-number-of-calls-in-half-open-state` | `3` |
| `resilience4j.retry.instances.merchant-service.max-attempts` | `3` |
| `resilience4j.retry.instances.merchant-service.wait-duration` | `500ms` |
| `resilience4j.retry.instances.payment-service.max-attempts` | `3` |
| `resilience4j.retry.instances.payment-service.wait-duration` | `500ms` |
| `app.webhook.delivery.redis-key` | `webhook-retry` |
| `app.webhook.delivery.poll-batch-size` | `100` |

### profile: `k8s`

| Key | Value |
|---|---|
| `spring.config.activate.on-profile` | `k8s` |
| `spring.datasource.url` | `jdbc:postgresql://postgres.neopay-core.svc.cluster.local:5432/neopay-operations-db` |
| `spring.datasource.username` | `operations_user` |
| `spring.datasource.password` | `${OPERATIONS_DB_PASSWORD}` |

## payment-service

### profile: `default`

| Key | Value |
|---|---|
| `server.port` | `9020` |
| `spring.datasource.url` | `${PAYMENT_DB_URL:jdbc:postgresql://localhost:5432/neopay-payment-db}` |
| `spring.cloud.openfeign.client.config.merchant-service.connectTimeout` | `2000` |
| `spring.cloud.openfeign.client.config.merchant-service.readTimeout` | `3000` |
| `spring.cloud.openfeign.client.config.vault-service.connectTimeout` | `2000` |
| `spring.cloud.openfeign.client.config.vault-service.readTimeout` | `5000` |
| `resilience4j.circuitbreaker.instances.merchant-service.sliding-window-size` | `10` |
| `resilience4j.circuitbreaker.instances.merchant-service.failure-rate-threshold` | `50` |
| `resilience4j.circuitbreaker.instances.merchant-service.wait-duration-in-open-state` | `10s` |
| `resilience4j.circuitbreaker.instances.merchant-service.permitted-number-of-calls-in-half-open-state` | `3` |
| `resilience4j.circuitbreaker.instances.vault-service.sliding-window-size` | `10` |
| `resilience4j.circuitbreaker.instances.vault-service.failure-rate-threshold` | `50` |
| `resilience4j.circuitbreaker.instances.vault-service.wait-duration-in-open-state` | `15s` |
| `resilience4j.circuitbreaker.instances.vault-service.permitted-number-of-calls-in-half-open-state` | `3` |
| `resilience4j.retry.instances.merchant-service.max-attempts` | `5` |
| `resilience4j.retry.instances.merchant-service.wait-duration` | `1s` |
| `resilience4j.retry.instances.vault-service.max-attempts` | `5` |
| `resilience4j.retry.instances.vault-service.wait-duration` | `1s` |

### profile: `k8s`

| Key | Value |
|---|---|
| `spring.config.activate.on-profile` | `k8s` |
| `spring.datasource.url` | `jdbc:postgresql://postgres.neopay-core.svc.cluster.local:5432/neopay-payment-db` |
| `spring.datasource.username` | `payment_user` |
| `spring.datasource.password` | `${PAYMENT_DB_PASSWORD}` |
| `stripe.sandbox.base-url` | `${STRIPE_SANDBOX_BASE_URL:https://api.stripe.com}` |
| `stripe.sandbox.api-key` | `${STRIPE_SANDBOX_API_KEY:}` |
| `stripe.sandbox.webhook-secret` | `${STRIPE_WEBHOOK_SECRET:}` |

## vault-service

### profile: `default`

| Key | Value |
|---|---|
| `server.port` | `9040` |
| `spring.datasource.url` | `${VAULT_DB_URL:jdbc:postgresql://localhost:5432/neopay-vault-db}` |
| `vault.master-key` | `${VAULT_MASTER_KEY:5hnhonOrsz9METLPM4C6sxdVuC0+IPy1c3WghMMqfuE=}` |
| `resilience4j.thread-pool-bulkhead.instances.vault-card-processor.core-thread-pool-size` | `5` |
| `resilience4j.thread-pool-bulkhead.instances.vault-card-processor.max-thread-pool-size` | `10` |
| `resilience4j.thread-pool-bulkhead.instances.vault-card-processor.queue-capacity` | `20` |

### profile: `k8s`

| Key | Value |
|---|---|
| `spring.config.activate.on-profile` | `k8s` |
| `spring.datasource.url` | `jdbc:postgresql://postgres.neopay-core.svc.cluster.local:5432/neopay-vault-db` |
| `spring.datasource.username` | `vault_user` |
| `spring.datasource.password` | `${VAULT_DB_PASSWORD}` |
| `vault.master-key` | `${VAULT_MASTER_KEY}` |
