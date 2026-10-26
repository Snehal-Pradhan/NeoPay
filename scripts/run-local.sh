#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export REDIS_PORT="${REDIS_PORT:-6380}"
export KAFKA_BROKERS="${KAFKA_BROKERS:-localhost:29092}"
export MERCHANT_DB_URL="${MERCHANT_DB_URL:-jdbc:postgresql://localhost:5433/neopay-merchant-db}"
export PAYMENT_DB_URL="${PAYMENT_DB_URL:-jdbc:postgresql://localhost:5433/neopay-payment-db}"
export OPERATIONS_DB_URL="${OPERATIONS_DB_URL:-jdbc:postgresql://localhost:5433/neopay-operations-db}"
export VAULT_DB_URL="${VAULT_DB_URL:-jdbc:postgresql://localhost:5433/neopay-vault-db}"
export DB_USER="${DB_USER:-neopay}"
export DB_PASSWORD="${DB_PASSWORD:-password}"
export CONFIG_SERVER_URL="${CONFIG_SERVER_URL:-http://localhost:8888}"
export EUREKA_URL="${EUREKA_URL:-http://localhost:8761/eureka/}"
# Simulators are opt-in; local and CI stacks need them to settle the flow.
export PAYMENT_SIMULATOR_ENABLED="${PAYMENT_SIMULATOR_ENABLED:-true}"
export OPERATIONS_SETTLEMENT_SIMULATOR_ENABLED="${OPERATIONS_SETTLEMENT_SIMULATOR_ENABLED:-true}"

mkdir -p .run/logs

start() {
  local name="$1" jar="$2"
  echo "starting $name"
  nohup java -XX:MaxRAMPercentage=75 -XX:+UseZGC -XX:+ZGenerational -jar "$jar" >".run/logs/${name}.log" 2>&1 &
  echo $! >".run/${name}.pid"
}

start discovery-service discovery-service/target/discovery-service-0.0.1-SNAPSHOT.jar
sleep 8
start config-service config-service/target/config-service-0.0.1-SNAPSHOT.jar
sleep 8
start merchant-service merchant-service/target/merchant-service-0.0.1-SNAPSHOT.jar
start payment-service payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
start vault-service vault-service/target/vault-service-0.0.1-SNAPSHOT.jar
start operations-service operations-service/target/operations-service-0.0.1-SNAPSHOT.jar
sleep 12
start api-gateway-service api-gateway-service/target/api-gateway-service-0.0.1-SNAPSHOT.jar

echo "started; logs in .run/logs; gateway on http://localhost:8080"
