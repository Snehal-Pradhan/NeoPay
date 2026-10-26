#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
mvn -q -f common-lib/pom.xml -DskipTests install
for svc in discovery-service config-service api-gateway-service merchant-service payment-service vault-service operations-service; do
  echo "building $svc"
  mvn -q -f "$svc/pom.xml" -DskipTests -Djib.skip=true package
done
echo "all modules built"
