import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.generic.storage import Storage

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_vault_flow", outformat="png"):
    api = node(Server, "vault API")
    crypto = node(Server, "crypto")
    processor = node(Server, "card processor")
    repo = node(Server, "repositories")
    db = node(PostgreSQL, "vault-db")
    key = node(Storage, "master key")

    api >> crypto
    crypto >> repo >> db
    crypto >> Edge(style="dashed", label="unwrap") >> key
    api >> processor
