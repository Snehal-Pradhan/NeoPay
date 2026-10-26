# C4 — Components: payment-service

```mermaid
flowchart TB
    subgraph inbound[Inbound adapters]
        orderCtl[OrderController]
        payCtl[PaymentController]
        internalCtl[InternalSettlementController]
    end

    subgraph app[Application]
        orderSvc[OrderService]
        paySvc[PaymentService]
        lookupSvc[PaymentLookupService]
        transition[PaymentTransitionService]
        stateMachine[PaymentStateMachine]
        recorder[PaymentAuthorizationRecorder]
    end

    subgraph domain[Domain]
        aggregate[PaymentAggregate]
        money[Money]
        ids[PaymentId / MerchantId / OrderId]
    end

    subgraph outbound[Outbound adapters]
        gateway[PaymentGatewayRouter]
        adapters[Card / UPI / NetBanking adapters]
        processor[PaymentProcessorRouter]
        outbox[OutboxEventPublisher]
        metrics[PaymentMetrics]
    end

    subgraph persistence[Persistence]
        orderRepo[OrderRepository]
        payRepo[PaymentRepository]
        outboxRepo[OutboxEventRepository]
        transitionRepo[PaymentTransitionLogRepository]
    end

    orderCtl --> orderSvc
    payCtl --> paySvc
    internalCtl --> lookupSvc

    paySvc --> recorder
    paySvc --> gateway
    paySvc --> transition
    orderSvc --> orderRepo
    recorder --> payRepo
    recorder --> outbox
    recorder --> transition
    transition --> stateMachine
    transition --> transitionRepo
    gateway --> adapters
    adapters --> processor
    paySvc --> lookupSvc
    lookupSvc --> payRepo
    paySvc --> metrics
    recorder --> metrics
    paySvc --> aggregate
    aggregate --> money
    aggregate --> ids
```

## Component rules

| Rule | Enforcement |
|---|---|
| Controller → service only | ArchUnit |
| Repository never depends on service | ArchUnit |
| Entity never depends on web or repository | ArchUnit |
| No `@Autowired` field injection | ArchUnit |
| At most five parameters per method | ArchUnit |
| No package cycles | ArchUnit |
