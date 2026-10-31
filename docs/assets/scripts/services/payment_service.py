import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.network import Internet
from diagrams.onprem.queue import Kafka

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline", "ranksep": "0.75"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_payment_flow", outformat="png"):
    psp = node(Internet, "PSP")
    api = node(Server, "payment API")
    with Cluster("payment-service", graph_attr={"margin": "18", "fontsize": "11", "labeljust": "l"}):
        saga = node(Server, "saga")
        state = node(Server, "states")
        ledger = node(Server, "ledger")
        repo = node(Server, "repositories")
        outbox = node(Server, "outbox")
    db = node(PostgreSQL, "payment-db")
    bus = node(Kafka, "Kafka")

    psp >> Edge(label="webhook") >> api >> saga >> state >> ledger >> repo >> db >> bus
    saga >> Edge(style="dashed") >> outbox
    outbox >> repo
    saga >> Edge(label="charge", style="dashed", constraint="false") >> psp
