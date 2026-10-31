from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.queue import Kafka
from diagrams.onprem.compute import Server
from diagrams.generic.storage import Storage

with Diagram("NeoPay storage topology", show=False, direction="LR",
             filename="./docs/assets/01_storage_architecture", outformat="png"):
    with Cluster("Relational (Postgres 16, local single node)"):
        merchant_db = PostgreSQL("merchant-db")
        payment_db = PostgreSQL("payment-db")
        operations_db = PostgreSQL("operations-db")
        vault_db = PostgreSQL("vault-db")

    with Cluster("Cache"):
        redis = Redis("redis\nrate limit\nidempotency marker\nShedLock")

    with Cluster("Stream"):
        kafka = Kafka("kafka\ndomain events\nwebhook retries")

    with Cluster("Object storage (not used)"):
        s3 = Storage("S3/MinIO\nplaceholder")

    with Cluster("Read path"):
        replica = PostgreSQL("read replica\nnot provisioned")

    merchant_db >> Edge(style="dashed", label="future replica") >> replica
    payment_db >> Edge(style="dashed", label="future replica") >> replica
    payment_db >> Edge(label="outbox drain") >> kafka
    kafka >> Edge(label="consume") >> Server("operations")
