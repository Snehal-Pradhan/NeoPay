import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.client import Users
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.network import Internet
from diagrams.onprem.queue import Kafka

G = dict(GRAPH_ATTR)
G.update({"nodesep": "0.45", "ranksep": "0.9", "splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/00_container_architecture", outformat="png"):
    users = node(Users, "Merchants")
    psp = node(Internet, "PSP")
    gateway = node(Server, "gateway")
    merchant = node(Server, "merchant")
    payment = node(Server, "payment")
    operations = node(Server, "operations")
    vault = node(Server, "vault")
    db = node(PostgreSQL, "Postgres")
    cache = node(Redis, "Redis")
    bus = node(Kafka, "Kafka")

    users >> Edge(label="API key") >> gateway
    psp >> Edge(label="webhook") >> gateway
    gateway >> merchant
    gateway >> payment
    gateway >> operations
    gateway >> vault
    merchant >> db
    payment >> db
    operations >> db
    vault >> db
    gateway >> cache
    payment >> Edge(label="outbox") >> bus
