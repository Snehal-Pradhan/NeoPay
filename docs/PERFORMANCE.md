# Performance

## Targets

| Path | Metric | Budget |
|---|---|---|
| `POST /v1/payments` (service level) | p99 | < 300 ms |
| `POST /v1/orders` (HTTP, k6) | p95 | < 300 ms |
| `POST /v1/payments` (HTTP, k6) | p99 | < 800 ms |
| Any path | error rate | < 1% |

## Measured baseline

`PaymentLatencyTest`, 300 samples after 50 warmup iterations, Postgres 16 in
Testcontainers, gateway router mocked.

| Percentile | Latency |
|---|---:|
| p50 | 6 ms |
| p95 | 9 ms |
| p99 | 11 ms |

Scope: order insert, payment insert, state transition, transition log, outbox write and
the mocked gateway call. Excludes HTTP serialization, the gateway process, the vault
service, and real processor latency.

## How to reproduce

```bash
mvn -f payment-service/pom.xml test -Dtest=PaymentLatencyTest
```

The test fails when p99 exceeds 300 ms, so it is also the regression gate.

Full HTTP path with k6:

```bash
k6 run -e BASE_URL=http://localhost:8080 \
       -e KEY_ID=np_test_xxx \
       -e KEY_SECRET=xxx \
       perf/k6-payment.js
```

Thresholds in `perf/k6-payment.js` fail the run on regression.

## Changes made for this measurement

| Change | Reason |
|---|---|
| `spring.jpa.show-sql: false` | SQL logging dominated request time |
| `spring.jpa.open-in-view: false` | Prevented connection retention during view rendering |
| Hikari `maximum-pool-size: 20`, `minimum-idle: 5`, `connection-timeout: 3000` | Bounded pool, fail fast instead of queueing |
| Index `idx_outbox_status_created` on `(status, created_at)` | Outbox poller scans by status ordered by age |
| Index `idx_payment_status_created` on `(status, created_at)` | Bank callback simulator scans `AUTHORIZING` by age |
| `-XX:MaxRAMPercentage=75 -XX:+UseZGC -XX:+ZGenerational` | Container-aware heap, low pause collector |

## Identified bottleneck

The outbox poller and the callback simulator both filtered on `status` and ordered by
`created_at` with no matching composite index. At low volume this is invisible; as the
outbox and payment tables grow it becomes a sequential scan per poll, every five
seconds, per replica.

Fix: composite indexes on `(status, created_at)` for both tables. Verify with:

```sql
explain analyze
select * from outbox_event where status = 'PENDING' order by created_at asc limit 100;
```

Expected plan: `Index Scan using idx_outbox_status_created`.

## Profiling

Not yet captured. Procedure:

```bash
# JFR, 60 seconds, during a k6 run
java -XX:StartFlightRecording=duration=60s,filename=/tmp/payment.jfr \
     -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
jfr print --events jdk.ExecutionSample /tmp/payment.jfr > /tmp/flame.txt
```

Alternative, sampling profiler:

```bash
asprof -d 60 -e cpu -f /tmp/flame.html <pid>
```

A committed flame graph is still outstanding.

## Known limits of this measurement

- Local container, single node, no network hop to vault.
- Gateway is mocked; the real card path adds a Feign call and vault decryption.
- No sustained soak; 300 sequential samples, not concurrent load.
- No JVM warmup beyond 50 iterations.
