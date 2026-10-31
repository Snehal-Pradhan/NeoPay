# Observability

Three signals, three purposes: traces locate a slow call, metrics detect a condition, logs explain a single failure.

## Tracing

| Property | Value |
|---|---|
| Library | Micrometer Tracing with Brave |
| Exporter | Zipkin over HTTP |
| Endpoint | `http://zipkin:9411/api/v2/spans` in the cluster |
| Sampling | 1.0 in the demo configuration |
| Propagation | W3C trace context between services and through the gateway |

A payment request produces one trace across gateway, payment, vault or merchant, and the outbox relay.

Source: `payment-service/src/main/resources/application.yaml`, `k8s/stateful/zipkin.yaml`.

## Metrics

| Metric | Type | Owner | Meaning |
|---|---|---|---|
| `outbox_pending_count` | gauge | `PaymentMetrics` | Rows awaiting relay |
| `outbox_lag_seconds` | gauge | `PaymentMetrics` | Age of the oldest pending row |
| `ledger_unbalanced_transactions` | gauge | `LedgerInvariantChecker` | Transactions whose debits differ from credits |
| `reserve_released_total` | counter | `ReserveReleaseService` | Reserve releases performed |
| `risk_unavailable_total` | counter | `RiskEnforcer` | Fail-open decisions |
| `payment_initiated_total` | counter | `PaymentMetrics` | Initiation volume by method |
| `payment_failed_total` | counter | `PaymentMetrics` | Failures by code |
| `payment_captured_total` | counter | `PaymentMetrics` | Captures by method |
| `webhook_delivery_total` | counter | `WebhookMetrics` | Delivery outcomes by status |
| `recon_breaks_total` | counter | `ReconService` | Reconciliation breaks by type |

Endpoint: `/actuator/prometheus`. Scrape configuration: `k8s/stateful/prometheus.yaml`, dashboards in `k8s/stateful/grafana.yaml`.

## Logs

| Property | Value |
|---|---|
| Format | JSON via logback encoder |
| Fields | timestamp, level, logger, thread, message, trace id, span id, service |
| Correlation | Trace id from Micrometer |
| Card data | Masked in `toString`; the PCI gate fails the build if a PAN field appears |

Log files for the local stack: `.run/logs/<service>.log`.

## Detection to diagnosis path

| Signal fires | First query | Then |
|---|---|---|
| `outbox_lag_seconds` above 60 | `select status, count(*) from outbox_event group by status` | Check poller lease and error text |
| `ledger_unbalanced_transactions` above 0 | `select transaction_id from ledger_line group by 1 having sum(debit_units) <> sum(credit_units)` | Inspect the posting caller for that transaction |
| `webhook_delivery_total{status="DEAD"}` rises | `select * from webhook_event where status='DEAD'` | Probe the merchant endpoint, then requeue |
| `429` rate rises | Gateway limiter counters | Compare request rate per key with the 200/min limit |

Procedures: [runbook](../runbook.md).

## Gaps

| Gap | Consequence |
|---|---|
| No alert rules in the repository | Conditions are visible, not paged |
| No SLO definitions | No burn-rate alerting |
| Sampling fixed at 1.0 | Costly at production volume |
| No log aggregation service in the local stack | Logs stay on disk per service |

Related: [deployment](02_deployment_and_hpa.md), [chaos](../CHAOS.md).
