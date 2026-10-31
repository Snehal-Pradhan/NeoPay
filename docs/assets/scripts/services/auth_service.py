from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.inmemory import Redis

with Diagram("auth module in merchant-service", show=False, direction="LR",
             filename="./docs/assets/service_auth_flow", outformat="png"):
    with Cluster("merchant-service"):
        controller = Server("AuthController\nsignup, login, keys")
        service = Server("AuthService / ApiKeyService")
        jwt = Server("JwtIssuer\nHS256")
        repo = Server("AppUserRepository\nApiKeyRepository")
    db = PostgreSQL("merchant-db")
    cache = Redis("Redis\napi key cache")

    controller >> service
    service >> jwt
    service >> repo >> db
    service >> Edge(label="cache key id") >> cache
