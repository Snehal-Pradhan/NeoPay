import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Kafka
from diagrams.onprem.compute import Server

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/03_outbox_pattern", outformat="png"):
    write = node(Server, "domain write")
    payment = node(PostgreSQL, "payment row")
    outbox = node(PostgreSQL, "outbox row")
    poller = node(Server, "poller")
    bus = node(Kafka, "Kafka")
    result = node(Server, "result handler")

    write >> payment
    write >> outbox
    outbox >> Edge(label="select") >> poller >> Edge(label="publish") >> bus
    bus >> Edge(style="dashed", label="ack", constraint="false") >> result
    result >> Edge(style="dashed", label="mark", constraint="false") >> outbox
