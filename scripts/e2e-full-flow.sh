#!/usr/bin/env bash
#
# Full-stack end-to-end check: gateway -> merchant -> payment -> vault -> callback
# -> settlement -> webhook.
#
# Requires the local stack to be running:
#   docker compose up -d
#   ./build-all.sh
#   ./scripts/run-local.sh
#
# Exits non-zero on the first failed assertion.

set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
DB_CONTAINER="${DB_CONTAINER:-neopay-ms-postgres}"
DB_USER="${DB_USER:-neopay}"
EMAIL="e2e-$(date +%s)@example.com"
PASSWORD="password123"

say() { printf '%s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
json() { jq -r "$1"; }

say "1. signup"
SIGNUP=$(curl -sS -X POST "$BASE/v1/auth/signup" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"E2E Merchant\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"businessType\":\"LLP\"}")
MERCHANT_ID=$(echo "$SIGNUP" | json .id)
[ -n "$MERCHANT_ID" ] && [ "$MERCHANT_ID" != "null" ] || fail "signup: $SIGNUP"

say "2. login"
TOKEN=$(curl -sS -X POST "$BASE/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | json .accessToken)
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || fail "login failed"

say "3. api key"
KEY=$(curl -sS -X POST "$BASE/v1/merchants/api-keys" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"environment":"TEST"}')
KEY_ID=$(echo "$KEY" | json .keyId)
KEY_SECRET=$(echo "$KEY" | json .keySecret)
BASIC=$(printf '%s:%s' "$KEY_ID" "$KEY_SECRET" | base64)
[ -n "$KEY_SECRET" ] && [ "$KEY_SECRET" != "null" ] || fail "api key: $KEY"

say "4. order"
ORDER=$(curl -sS -X POST "$BASE/v1/orders" \
  -H "Authorization: Basic $BASIC" \
  -H 'Content-Type: application/json' \
  -d '{"amount":{"amountUnits":5000,"currency":"INR"},"receipt":"e2e-'"$(date +%s)"'"}')
ORDER_ID=$(echo "$ORDER" | json .id)
[ -n "$ORDER_ID" ] && [ "$ORDER_ID" != "null" ] || fail "order: $ORDER"

say "5. tokenize card"
TOKENIZE=$(curl -sS -X POST "$BASE/v1/vault/tokenize" \
  -H "Authorization: Basic $BASIC" \
  -H 'Content-Type: application/json' \
  -d '{"pan":"4111111111111111","cvv":"123","expiryMonth":12,"expiryYear":2030,"cardHolderName":"E2E User"}')
VAULT_TOKEN=$(echo "$TOKENIZE" | json .token)
[ -n "$VAULT_TOKEN" ] && [ "$VAULT_TOKEN" != "null" ] || fail "tokenize: $TOKENIZE"

say "6. initiate payment (idempotent)"
IDEMPOTENCY="e2e-$(date +%s)"
PAYMENT=$(curl -sS -X POST "$BASE/v1/payments" \
  -H "Authorization: Basic $BASIC" \
  -H 'Content-Type: application/json' \
  -H "X-Idempotency-Key: $IDEMPOTENCY" \
  -d "{\"orderId\":\"$ORDER_ID\",\"method\":\"CARD\",\"methodDetails\":{\"token\":\"$VAULT_TOKEN\"}}")
PAYMENT_ID=$(echo "$PAYMENT" | json .id)
[ -n "$PAYMENT_ID" ] && [ "$PAYMENT_ID" != "null" ] || fail "payment: $PAYMENT"

REPLAY=$(curl -sS -X POST "$BASE/v1/payments" \
  -H "Authorization: Basic $BASIC" \
  -H 'Content-Type: application/json' \
  -H "X-Idempotency-Key: $IDEMPOTENCY" \
  -d "{\"orderId\":\"$ORDER_ID\",\"method\":\"CARD\",\"methodDetails\":{\"token\":\"$VAULT_TOKEN\"}}")
REPLAY_ID=$(echo "$REPLAY" | json .id)
[ "$REPLAY_ID" = "$PAYMENT_ID" ] || fail "idempotency: $REPLAY_ID != $PAYMENT_ID"

say "7. wait for capture"
STATUS=""
for _ in $(seq 1 30); do
  STATUS=$(curl -sS "$BASE/v1/orders/$ORDER_ID/payments" -H "Authorization: Basic $BASIC" \
    | jq -r --arg id "$PAYMENT_ID" '.[] | select(.id==$id) | .status' 2>/dev/null || true)
  [ "$STATUS" = "CAPTURED" ] && break
  sleep 2
done
[ "$STATUS" = "CAPTURED" ] || fail "payment not captured (last status: $STATUS)"

say "8. confirm one payment row"
ROWS=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d neopay-payment-db -tAc \
  "select count(*) from payment where id='$PAYMENT_ID';")
[ "$ROWS" = "1" ] || fail "expected 1 payment row, found $ROWS"

say "9. confirm outbox published"
PENDING=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d neopay-payment-db -tAc \
  "select count(*) from outbox_event where status='PENDING';")
[ "$PENDING" = "0" ] || fail "outbox still has $PENDING pending rows"

say "10. confirm webhook delivered"
DELIVERED=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d neopay-operations-db -tAc \
  "select count(*) from webhook_event where merchant_id='$MERCHANT_ID' and status='DELIVERED';")
[ "$DELIVERED" -ge 1 ] || fail "no delivered webhook for merchant"

say "PASS merchant=$MERCHANT_ID payment=$PAYMENT_ID status=$STATUS webhooks=$DELIVERED"
