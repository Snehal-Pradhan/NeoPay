from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.generic.storage import Storage

with Diagram("config-service flow", show=False, direction="LR",
             filename="./docs/assets/service_config_flow", outformat="png"):
    with Cluster("config-service"):
        api = Server("ConfigServer\nnative profile")
        repo = Storage("classpath:/config\nper-service yml")
    clients = Server("7 services\nspring.config.import")

    api >> repo
    clients >> Edge(label="GET /{service}/{profile}") >> api
