# Runbook

Operational procedures for the payment platform. Each entry states how to detect the
condition, what to check, what to do, and how to confirm recovery.

---

## 1. Outbox is stalled

**Detect:** `outbox_lag_seconds` rises above 60, or `outbox_pending_count` grows
monotonically in Grafana.

**Check**

```sql
select status, count(*), min(created_at), max(created_at)
from outbox_event
group by status;
```

```bash
# ShedLock key present?
docker exec redis redis-cli --scan --pattern '*job-lock*'
```

**Act**

1. Confirm the poller pod is running and healthy.
2. If the lock is held by a dead pod, wait for `lockAtMostFor` to expire; do not delete
   the key unless the holder is confirmed dead.
3. If rows are `FAILED`, inspect the error, fix the cause, then reset them to `PENDING`:
   `update outbox_event set status='PENDING' where status='FAILED';`

**Verify:** lag returns below 10 seconds and pending count trends to zero.

---

## 2. Webhook DLQ is filling

**Detect:** `webhook_delivery_total{status="DEAD"}` increases, or `dlq_event` count
rises.

**Check:** the merchant endpoint, response codes, and `last_response_body`.

```sql
select event_type, attempts, last_response_code, last_response_body
from webhook_event
where status = 'DEAD'
order by created_at desc
limit 20;
```

**Act**

1. If the merchant endpoint is down, pause delivery for that target.
2. If retries are exhausted but the endpoint is healthy now, requeue:
   `update webhook_event set status='PENDING', attempts=0 where status='DEAD';`
3. Never requeue blindly in bulk; confirm the endpoint accepts signed payloads.

**Verify:** new events reach `DELIVERED` and the DLQ stops growing.

---

## 3. Redis is unavailable

**Detect:** 5xx from gateway, `RedisConnectionFailureException` in logs.

**Impact**

| Feature | Behaviour |
|---|---|
| Idempotency marker | Fails open; the database unique index still blocks duplicates |
| Rate limiting | Fails open; traffic is not throttled |
| ShedLock | Scheduled jobs pause |
| API key cache | Falls back to the database on every request |

**Act:** restore Redis. Do not disable the idempotency database constraint.

**Verify:** gateway latency normalises and scheduled jobs resume.

---

## 4. Kafka consumer lag

**Detect:** consumer group lag on `payments.events` or `orders.events`.

**Check:** `operations-service` logs, partition assignment, and JVM health.

**Act**

1. Scale `operations-service` replicas up to the partition count.
2. If one partition is hot, check whether the producer key is skewed.
3. If poison messages loop, confirm they reach the dead-letter topic rather than
   blocking the partition.

**Verify:** lag decreases monotonically.

---

## 5. Settlement failed

**Detect:** settlement status `FAILED`, or `settlement_failed_total` increases.

**Check**

```sql
select id, merchant_id, net_amount_units, status, failure_reason
from settlement
where status = 'FAILED'
order by created_at desc;
```

**Act**

1. Distinguish a bank-transfer failure from a data failure.
2. For a transfer failure, retry the payout for the same settlement; do not create a
   second settlement for the same payments.
3. Confirm the linked payments were not marked `SETTLED`.

**Verify:** settlement reaches `PROCESSED` and linked payments move to `SETTLED`
exactly once.

---

## 6. Idempotency conflict storm

**Detect:** spike in `409 IDEMPOTENCY_CONFLICT` or
`payment_idempotency_replay_total`.

**Act**

1. Confirm clients are reusing keys intentionally; replay is the correct behaviour.
2. If clients generate a new key per retry, that is a client defect: the retry creates
   a new payment attempt.
3. If a single key is stuck in `IN_PROGRESS`, its 30-second TTL expires automatically.

**Verify:** conflict rate falls after the client fix.

---

## 7. Payment stuck in `AUTHORIZING`

**Detect:** `AUTHORIZING` age above the simulator window.

**Act**

1. Check `BankCallbackSimulator` and its ShedLock key.
2. If the simulator is disabled, resolve manually through the callback endpoint.
3. Confirm the payment has a `processor_reference`; without one, the gateway call never
   landed and compensation should have fired.

**Verify:** payment reaches `CAPTURED` or `FAILED`; no row remains in `AUTHORIZING`
beyond the window.
