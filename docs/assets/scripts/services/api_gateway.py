import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.client import Users
from diagrams.onprem.compute import Server
from diagrams.onprem.inmemory import Redis

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_api_gateway_flow", outformat="png"):
    client = node(Users, "Merchants")
    auth = node(Server, "auth filter")
    limit = node(Server, "rate limit")
    idem = node(Server, "idempotency")
    router = node(Server, "router")
    cache = node(Redis, "Redis")
    payment = node(Server, "payment")
    merchant = node(Server, "merchant")
    operations = node(Server, "operations")
    vault = node(Server, "vault")

    client >> Edge(label="API key") >> auth >> limit >> idem >> router
    auth >> Edge(label="lookup", style="dashed") >> cache
    limit >> Edge(label="INCR", style="dashed") >> cache
    router >> payment
    router >> merchant
    router >> operations
    router >> vault
