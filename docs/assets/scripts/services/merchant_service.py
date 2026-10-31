import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_merchant_flow", outformat="png"):
    api = node(Server, "merchant API")
    svc = node(Server, "merchant service")
    repo = node(Server, "repositories")
    db = node(PostgreSQL, "merchant-db")
    outbox = node(Server, "outbox writer")
    bus = node(Kafka, "Kafka")

    api >> svc >> repo >> db
    svc >> outbox
    outbox >> Edge(label="relay", style="dashed", constraint="false") >> bus
