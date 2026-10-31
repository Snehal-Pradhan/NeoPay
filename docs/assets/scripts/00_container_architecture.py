from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.client import Users, Client
from diagrams.onprem.network import Internet
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.queue import Kafka
from diagrams.onprem.monitoring import Prometheus, Grafana

with Diagram("NeoPay container architecture", show=False, direction="LR",
             filename="./docs/assets/00_container_architecture", outformat="png"):
    users = Users("Merchants and customers")
    psp = Internet("Card and UPI processor")

    with Cluster("Edge"):
        gateway = Server("api-gateway\n:8080")
        discovery = Server("discovery\n:8761")
        config = Server("config\n:8888")

    with Cluster("Core services"):
        merchant = Server("merchant\n:9010")
        payment = Server("payment\n:9020")
        operations = Server("operations\n:9030")
        vault = Server("vault\n:9040")

    with Cluster("Data"):
        pg = PostgreSQL("Postgres\none database per service")
        cache = Redis("Redis\nrate limit, idempotency")
        bus = Kafka("Kafka\ndomain events")

    with Cluster("Observability"):
        zipkin = Prometheus("Zipkin")
        metrics = Grafana("Prometheus + Grafana")

    users >> Edge(label="API key / JWT") >> gateway
    gateway >> Edge(label="route") >> merchant
    gateway >> Edge(label="route") >> payment
    gateway >> Edge(label="route") >> operations
    gateway >> Edge(label="route") >> vault
    gateway >> Edge(label="public") >> psp

    merchant >> pg
    payment >> pg
    operations >> pg
    vault >> pg

    gateway >> cache
    payment >> cache
    payment >> Edge(label="outbox") >> bus
    operations >> bus
    payment >> Edge(label="webhook") >> psp

    gateway >> discovery
    merchant >> discovery
    payment >> discovery
    operations >> discovery
    vault >> discovery

    merchant >> config
    payment >> config
    operations >> config
    vault >> config

    payment >> Edge(style="dashed") >> zipkin
    operations >> Edge(style="dashed") >> metrics
