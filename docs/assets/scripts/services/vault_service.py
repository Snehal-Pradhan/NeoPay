from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.generic.storage import Storage

with Diagram("vault-service flow", show=False, direction="LR",
             filename="./docs/assets/service_vault_flow", outformat="png"):
    with Cluster("vault-service"):
        api = Server("VaultController\ntokenize, detokenize")
        crypto = Server("VaultEncryptionConfig\nper-card DEK + master key")
        processor = Server("CardPaymentProcessor")
        repo = Server("VaultCardRepository")
    db = PostgreSQL("vault-db\nencrypted PAN")
    kms = Storage("master key\nenvironment secret")

    api >> crypto
    crypto >> kms
    crypto >> repo >> db
    api >> processor
