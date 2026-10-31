import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_auth_flow", outformat="png"):
    api = node(Server, "auth API")
    service = node(Server, "auth service")
    jwt = node(Server, "JWT issuer")
    repo = node(Server, "repositories")
    db = node(PostgreSQL, "merchant-db")
    cache = node(Redis, "Redis")

    api >> service
    service >> jwt
    service >> repo >> db
    service >> Edge(style="dashed", label="cache") >> cache
