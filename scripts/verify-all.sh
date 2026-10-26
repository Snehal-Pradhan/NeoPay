#!/usr/bin/env bash
# Runs every module through the full quality gate: tests, ArchUnit, PMD,
# SpotBugs, ErrorProne and JaCoCo report.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODULES=(common-lib merchant-service payment-service vault-service operations-service
         api-gateway-service discovery-service config-service)

echo "== common-lib install =="
mvn -q -f common-lib/pom.xml -DskipTests install

for module in "${MODULES[@]}"; do
    echo "== $module =="
    mvn -q -f "$module/pom.xml" verify
done

echo "== layered coverage gate =="
python3 scripts/coverage-gate.py

echo "== complexity budget =="
./scripts/complexity-budget.sh

echo "all modules verified"
