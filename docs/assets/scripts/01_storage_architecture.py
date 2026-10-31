import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.queue import Kafka
from diagrams.onprem.compute import Server

G = dict(GRAPH_ATTR)
G.update({"nodesep": "0.6", "ranksep": "0.9", "splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/01_storage_architecture", outformat="png"):
    db = node(PostgreSQL, "payment-db")
    bus = node(Kafka, "Kafka")
    operations = node(Server, "operations")
    cache = node(Redis, "Redis")
    replica = node(PostgreSQL, "read replica")

    db >> Edge(label="outbox") >> bus >> Edge(label="consume") >> operations
    db >> Edge(style="dashed", label="future", constraint="false") >> replica
    operations >> Edge(style="dashed", label="cache", constraint="false") >> cache
