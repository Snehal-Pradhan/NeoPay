from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka

with Diagram("Transactional outbox", show=False, direction="LR",
             filename="./docs/assets/03_outbox_pattern", outformat="png"):
    with Cluster("payment-service, one transaction"):
        svc = Server("domain write")
        payment = PostgreSQL("payment row")
        outbox = PostgreSQL("outbox_event\nPENDING")
    db = PostgreSQL("payment-db")
    with Cluster("Relay"):
        poller = Server("OutboxPoller\nShedLock 1m")
        handler = Server("KafkaPublisher")
        result = Server("OutboxResultHandler\nPUBLISHED / FAILED")
    bus = Kafka("kafka topic")

    svc >> Edge(label="insert") >> payment
    svc >> Edge(label="insert same tx") >> outbox
    payment - outbox
    outbox >> Edge(label="select PENDING") >> db
    db >> poller >> handler >> bus
    handler >> result >> outbox
