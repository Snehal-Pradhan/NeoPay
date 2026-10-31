from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.client import Users
from diagrams.onprem.network import Internet
from diagrams.onprem.compute import Server
from diagrams.onprem.inmemory import Redis

with Diagram("api-gateway flow", show=False, direction="LR",
             filename="./docs/assets/service_api_gateway_flow", outformat="png"):
    client = Users("Merchant client")
    with Cluster("api-gateway-service"):
        auth = Server("GatewayAuthFilter\nAPI key, JWT")
        limit = Server("FixedWindowRateLimiter")
        idem = Server("IdempotencyFilter")
        router = Server("Spring Cloud Gateway\nroute table")
    cache = Redis("Redis\nkey cache + counters")
    with Cluster("Upstream"):
        merchant = Server("merchant :9010")
        payment = Server("payment :9020")
        operations = Server("operations :9030")
        vault = Server("vault :9040")
    psp = Internet("PSP webhook")

    client >> auth >> limit >> idem >> router
    auth >> Edge(label="lookup") >> cache
    limit >> Edge(label="INCR") >> cache
    router >> merchant
    router >> payment
    router >> operations
    router >> vault
    psp >> Edge(label="public /webhook/stripe") >> router
