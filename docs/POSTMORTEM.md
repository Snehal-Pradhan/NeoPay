# Defect Postmortems

Real defects found in this codebase. Format: symptom, root cause, blast radius, fix,
prevention.

---

## 1. Floating-point fee undercharge

**Symptom:** settlement fee was one paisa lower than 2% for some amounts.

**Root cause:** `double FEE_RATE = 0.02`. Binary floating point cannot represent 0.02,
so `25 * 0.02` evaluates to `0.49999999999999994` and rounds down. Every amount
congruent to 25 (mod 100) paise was affected  -  about 1 in 100.

**Blast radius:** systematic undercharging on settlement; GST inherited the error and
compounded it.

**Fix:** integer basis points, `Math.multiplyExact`, explicit HALF_UP rounding.

**Prevention:** boundary tests at 25, 125, 225, 525, 10025 and 100125 paise; a property
that `fee + gst + net == gross`; no floating point in money arithmetic.

---

## 2. Vault charge response failed to deserialize

**Symptom:** every card payment failed with
`PAYMENT_GATEWAY_ROUTER_UNREACHABLE`, cause
`Type definition error: simple type, class PaymentProcessorResponse`.

**Root cause:** the vault endpoint returned a sealed interface. The Feign client could
not reconstruct the subtype because the wire payload carried no discriminator.

**Blast radius:** all card payments.

**Fix:** a concrete `VaultChargeResponse` record with a `type` field, mapped at the
vault boundary.

**Prevention:** contract test pinning the response shape; do not put polymorphic types
on an RPC boundary without an explicit discriminator.

---

## 3. Kafka retried every message to death

**Symptom:** webhook events were never created; the log showed
`Backoff FixedBackOffExecution exhausted` for every record.

**Root cause:** the listener declared `Acknowledgment` but the container used the
default ack mode, so Spring threw
`No Acknowledgment available as an argument` before the handler body ran. The error
handler retried until exhaustion.

**Blast radius:** all webhook fan-out.

**Fix:** `spring.kafka.listener.ack-mode: manual`.

**Prevention:** assert a consumer actually processes one record in an integration test;
alert on consumer retry exhaustion.

---

## 4. Order and merchant status silently null

**Symptom:** API responses contained `status: null` while the database was correct.

**Root cause:** MapStruct matched by name. The entity field was `orderStatus` and the
DTO field was `status`; no mapping existed, so the value was dropped without error.

**Blast radius:** every order and merchant read response.

**Fix:** explicit `@Mapping(source = "orderStatus", target = "status")`.

**Prevention:** response-shape assertions in tests; MapStruct
`unmappedTargetPolicy = ERROR`.

---

## 5. Rate limiter NPE on null TTL

**Symptom:** none in production; found by SpotBugs (`NS_NON_SHORT_CIRCUIT`).

**Root cause:** `ttl != null & ttl > 0` used bitwise AND, evaluating both operands. A
null TTL would unbox and throw while computing `Retry-After`.

**Blast radius:** every rate-limited request that exceeded its window during a Redis
TTL anomaly.

**Fix:** `&&`.

**Prevention:** SpotBugs at threshold Medium bound to `verify`.

---

## 6. Negative simulator bucket

**Symptom:** none in production; found by SpotBugs
(`RV_ABSOLUTE_VALUE_OF_HASHCODE`).

**Root cause:** `Math.abs(uuid.hashCode())`. For `Integer.MIN_VALUE` the absolute value
remains negative, producing a negative bucket and a negative delay.

**Blast radius:** the affected payment would never be resolved by the simulator.

**Fix:** `Math.floorMod(hash, bound)`.

**Prevention:** static analysis; prefer `floorMod` for any modulo of a hash.
