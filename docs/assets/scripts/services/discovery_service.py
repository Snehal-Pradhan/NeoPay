import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from _style import GRAPH_ATTR, NODE_ATTR, EDGE_ATTR, node

from diagrams import Diagram, Edge
from diagrams.onprem.compute import Server

G = dict(GRAPH_ATTR)
G.update({"splines": "polyline"})

with Diagram("", show=False, direction="LR", graph_attr=G,
             node_attr=NODE_ATTR, edge_attr=EDGE_ATTR,
             filename="./docs/assets/service_discovery_flow", outformat="png"):
    gateway = node(Server, "gateway")
    merchant = node(Server, "merchant")
    payment = node(Server, "payment")
    operations = node(Server, "operations")
    vault = node(Server, "vault")
    registry = node(Server, "registry")

    gateway >> Edge(label="register") >> registry
    merchant >> Edge(constraint="false") >> registry
    payment >> Edge(constraint="false") >> registry
    operations >> Edge(constraint="false") >> registry
    vault >> Edge(constraint="false") >> registry
