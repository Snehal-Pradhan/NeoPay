from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka

with Diagram("operations-service flow", show=False, direction="LR",
             filename="./docs/assets/service_operations_flow", outformat="png"):
    with Cluster("operations-service"):
        api = Server("ReconController\nPayoutController\nSettlementController")
        settle = Server("SettlementTransactionExecutor\nSettlementFeeCalculator")
        recon = Server("ReconService")
        hook = Server("WebhookDeliverExecutor\nWebhookRetryQueue")
        repo = Server("Repositories")
    db = PostgreSQL("operations-db")
    bus = Kafka("kafka")
    merchant = Server("merchant-service")
    payment = Server("payment-service")

    api >> settle >> repo
    settle >> merchant
    settle >> payment
    api >> recon >> repo
    bus >> hook
    hook >> repo >> db
    hook >> Edge(label="signed POST") >> merchant
