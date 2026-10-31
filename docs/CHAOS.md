# Chaos and Recovery

## Purpose

Measure what happens to money when a dependency disappears. A passing integration suite
proves the happy path; it does not prove that a killed database or broker leaves the ledger
balanced and the outbox draining.

## Harness

| Script | Kills | Measures |
|---|---|---|
| `scripts/chaos/run-chaos.sh postgres` | `docker compose stop postgres` for 20 s | recovery seconds, k6 error rate, duplicate idempotency keys, pending outbox rows |
| `scripts/chaos/run-chaos.sh kafka` | `docker compose stop kafka` for 20 s | recovery seconds, outbox backlog drain after restart |

Flow: provision 20 merchant keys, start `perf/k6-payment.js` for 90 s, stop the target after
10 s, restart it, poll `/actuator/health` plus a real order creation until both succeed, then
wait for k6 and check the invariants.

Recovery time is measured from the stop timestamp to the first successful order, not to the
first successful TCP connect. A health endpoint that answers before the datasource is usable
would otherwise flatter the number.

## Invariants asserted after recovery

| Invariant | Query |
|---|---|
| No duplicate charges | group `payment` by `(merchant_id, idempotency_key)`, count groups with `count > 1` |
| Outbox drains | `select count(*) from outbox_event where status = 'PENDING'` returns to a bounded value |
| Ledger balanced | `LedgerInvariantChecker` gauge remains zero |

## Expected failure semantics

| Failure | Expected behaviour |
|---|---|
| Postgres down during initiate | request fails with a 5xx; no partial payment row; retry with the same idempotency key succeeds once |
| Postgres down during capture | transaction rolls back; payment stays `AUTHORIZING` or `AUTHORIZED`; a later capture is the only capture |
| Kafka down during publish | outbox row stays `PENDING`; no event loss; the poller drains after restart |
| Kafka down during webhook ingest | the webhook row commits; the outbox event is deferred, not dropped |

## Measured — 2026-10-31, local Docker stack

Machine: Apple silicon, Docker Desktop, single broker, single Postgres, seven JVM services,
Jib images not used (jars run directly). Load: 15 flows/s for 90 s, 20 s outage.

| Target | Outage | Recovery to successful order | Duplicate capture groups | Ledger debit−credit | Outbox drain after restart |
|---|---:|---:|---:|---:|---:|
| Postgres | 20 s | **28 s** | 0 | 0 | 155 → 0 within 30 s |
| Kafka | 20 s | **24 s** | 0 | 0 | 476 → 0 within 20 s |

Recovery is measured to the first successful `POST /v1/orders`, not to a health 200. The
outbox counts are the `PENDING` rows at the moment k6 ended; both drained to zero without
operator action.

Raw evidence: `perf/chaos-postgres.json`, `perf/chaos-kafka.json`, `perf/chaos-k6-*.log`.

## What the numbers mean

| Observation | Interpretation |
|---|---|
| 0 duplicate groups | the unique idempotency index survives a datasource outage |
| ledger delta 0 | no partial posting; the capture transaction is atomic |
| outbox drains unattended | the poller resumes and replays; no event loss |
| 24–28 s RTO | dominated by Postgres/broker restart, not by application backoff |

## Limits

- Single-node dependencies. A managed multi-AZ database has a different failure profile.
- No disk or network partition test, only process stop and start.
- 15 flows/s is not saturation; recovery under peak load is not measured here.
