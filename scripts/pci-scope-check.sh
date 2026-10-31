#!/usr/bin/env bash
# PCI SAQ-A scope gate.
#
# Card numbers (PAN) may be persisted only by vault-service. The payment, merchant,
# operations and gateway services may hold a token, a last-four and a brand, never a PAN.
# common-lib is excluded on purpose: PaymentProcessorRequest carries a PAN as a transient
# in-memory field that is never persisted and never logged.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SCANNED=(merchant-service payment-service operations-service api-gateway-service)
PATTERN='^[[:space:]]*(private|protected|public)[[:space:]]+(final[[:space:]]+)?String[[:space:]]+pan[[:space:]]*[;=]'

fail=0
for module in "${SCANNED[@]}"; do
    hits="$(grep -rnE "$PATTERN" "$module/src/main/java" --include='*.java' || true)"
    if [[ -n "$hits" ]]; then
        echo "PCI SCOPE VIOLATION in $module:"
        echo "$hits"
        fail=1
    fi
done

if [[ $fail -ne 0 ]]; then
    echo "FAIL: PAN field found outside vault-service"
    exit 1
fi

TOKENIZE="vault-service/src/main/java/com/snehal/neopay/vault/dto/request/TokenizeRequest.java"
if ! grep -qE 'String[[:space:]]+pan\b' "$TOKENIZE"; then
    echo "FAIL: positive control missing; $TOKENIZE no longer declares a PAN, gate is not exercising anything"
    exit 1
fi

echo "PCI scope OK: no PAN field outside vault-service (positive control: $TOKENIZE)"
