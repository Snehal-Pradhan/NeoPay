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

## Status

Harness committed. Numbers are recorded in `perf/chaos-<target>.json` when the run executes;
this document does not claim measurements it has not produced.
