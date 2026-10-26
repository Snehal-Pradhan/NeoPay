# C4 — System Context

```mermaid
C4Context
    title System Context — NeoPay Clone

    Person(merchant, "Merchant", "Creates orders, takes payments, receives webhooks")
    Person(customer, "Customer", "Pays through a checkout surface")

    System(platform, "NeoPay Clone", "Order, payment, tokenization, settlement and webhook platform")

    System_Ext(cardProcessor, "Card Processor", "Simulated authorization and capture")
    System_Ext(bank, "Bank / PSP", "Simulated settlement and callback")
    System_Ext(observability, "Observability", "Prometheus, Grafana, Zipkin")

    Rel(merchant, platform, "Creates API keys, orders and webhook targets", "HTTPS / JWT")
    Rel(customer, platform, "Pays", "HTTPS / API key")
    Rel(platform, cardProcessor, "Authorizes and captures cards", "Internal")
    Rel(platform, bank, "Settles funds", "Internal")
    Rel(platform, observability, "Emits metrics and traces", "OTLP / HTTP")
```

## Actors

| Actor | Responsibility |
|---|---|
| Merchant | Owns an account, API keys and webhook targets |
| Customer | Supplies payment credentials through a checkout surface |
| Operator | Reads dashboards, runbooks and reconciliation breaks |

## External systems

Card processor, bank/PSP and UPI switch are simulated. No real network, settlement
window or ISO 8583/20022 message is exchanged.
