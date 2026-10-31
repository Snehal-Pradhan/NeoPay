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
             filename="./docs/assets/service_operations_flow", outformat="png"):
    bus = node(Kafka, "Kafka")
    hook = node(Server, "webhook worker")
    api = node(Server, "internal API")
    settle = node(Server, "settlement")
    recon = node(Server, "recon")
    repo = node(Server, "repositories")
    db = node(PostgreSQL, "operations-db")
    payment = node(Server, "payment")
    merchant = node(Server, "merchant")

    bus >> Edge(label="consume") >> hook >> repo
    api >> settle >> repo >> db
    api >> recon >> repo
    settle >> payment
    settle >> merchant
