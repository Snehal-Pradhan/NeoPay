from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server

with Diagram("discovery-service flow", show=False, direction="LR",
             filename="./docs/assets/service_discovery_flow", outformat="png"):
    with Cluster("discovery-service"):
        registry = Server("Eureka server\nregistry")
    with Cluster("Clients"):
        gateway = Server("api-gateway")
        merchant = Server("merchant")
        payment = Server("payment")
        operations = Server("operations")
        vault = Server("vault")

    gateway >> Edge(label="register / fetch") >> registry
    merchant >> registry
    payment >> registry
    operations >> registry
    vault >> registry
