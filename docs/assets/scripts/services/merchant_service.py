from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka

with Diagram("merchant-service flow", show=False, direction="LR",
             filename="./docs/assets/service_merchant_flow", outformat="png"):
    with Cluster("merchant-service"):
        api = Server("MerchantController\nKycController\nWebhookConfigController")
        svc = Server("MerchantService\nKycService\nLimitService")
        outbox = Server("OutboxEventPublisher")
        repo = Server("Repositories")
    db = PostgreSQL("merchant-db")
    bus = Kafka("kafka")

    api >> svc >> repo >> db
    svc >> outbox >> db
    db >> Edge(label="poller") >> bus
