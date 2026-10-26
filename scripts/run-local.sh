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
# Deterministic approvals: the NORMAL mode declines a share of payments by hash,
# which makes an end-to-end run flaky for no useful reason.
export PAYMENT_SIMULATOR_CHAOS_MODE="${PAYMENT_SIMULATOR_CHAOS_MODE:-SUCCESS}"

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

wait_health() {
  local name="$1" port="$2"
  for _ in $(seq 1 60); do
    if curl -fsS "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
      echo "$name healthy on $port"
      return 0
    fi
    sleep 2
  done
  echo "$name FAILED to become healthy on $port; see .run/logs/$name.log" >&2
  return 1
}

wait_health discovery-service 8761
wait_health config-service 8888
wait_health merchant-service 9010 || exit 1
wait_health payment-service 9020 || exit 1
wait_health vault-service 9040 || exit 1
wait_health operations-service 9030 || exit 1
wait_health api-gateway-service 8080 || exit 1

echo "started; logs in .run/logs; gateway on http://localhost:8080"
