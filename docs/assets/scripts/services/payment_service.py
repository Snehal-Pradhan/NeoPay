from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.queue import Kafka
from diagrams.onprem.network import Internet

with Diagram("payment-service flow", show=False, direction="LR",
             filename="./docs/assets/service_payment_flow", outformat="png"):
    with Cluster("payment-service"):
        api = Server("PaymentController\nOrderController\nDisputeController\nStripeWebhookController")
        saga = Server("PaymentAuthorizationRecorder\nSaga compensation")
        state = Server("PaymentStateMachine\nTransitionService")
        ledger = Server("LedgerService\ndouble entry")
        outbox = Server("OutboxEventPublisher")
        repo = Server("Repositories")
    db = PostgreSQL("payment-db")
    cache = Redis("Redis\nidempotency marker")
    bus = Kafka("kafka")
    psp = Internet("PSP / simulator")

    api >> saga >> state
    saga >> psp
    saga >> ledger >> repo
    saga >> outbox >> repo
    api >> cache
    api >> repo >> db
    db >> Edge(label="poller") >> bus
    psp >> Edge(label="signed webhook") >> api
