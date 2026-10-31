// Soak profile: ramp to a sustained rate and hold it long enough for pools, GC and
// connection counts to stabilise. Same flow as k6-payment.js; different load shape.
//
// Env: BASE_URL, AUTH_LIST (comma-separated keyId:keySecret), PEAK_RATE, HOLD.
import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PEAK = Number(__ENV.PEAK_RATE || 100);
const HOLD = __ENV.HOLD || '15m';
// AUTH_POOL is a comma-separated list of base64(keyId:keySecret), matching k6-payment.js.
const AUTH = (__ENV.AUTH_POOL || '').split(',').map((v) => v.trim()).filter(Boolean)
  .map((value) => ({ basic: `Basic ${value}` }));

export const options = {
  scenarios: {
    soak: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: Math.max(50, PEAK),
      maxVUs: Math.max(200, PEAK * 3),
      stages: [
        { duration: '2m', target: PEAK },
        { duration: HOLD, target: PEAK },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:create_order}': ['p(99)<600'],
    'http_req_duration{name:init_payment}': ['p(99)<600'],
  },
};

const failures = new Counter('flow_failures');
const orderLatency = new Trend('order_latency', true);
const paymentLatency = new Trend('payment_latency', true);

export default function () {
  if (AUTH.length === 0) throw new Error('AUTH_POOL is empty');
  const creds = AUTH[__VU % AUTH.length];
  const headers = {
    'Content-Type': 'application/json',
    Authorization: creds.basic,
  };
  const receipt = `soak-${__VU}-${__ITER}-${Date.now()}`;

  const order = http.post(`${BASE}/v1/orders`, JSON.stringify({
    amount: { amountUnits: 2500, currency: 'INR' }, receipt,
  }), { headers, tags: { kind: 'order', name: 'create_order' } });
  orderLatency.add(order.timings.duration);
  if (!check(order, { 'order 2xx': (r) => r.status >= 200 && r.status < 300 })) {
    failures.add(1);
    console.log(`ORDER_FAIL ${order.status} ${String(order.body).slice(0, 160)}`);
    return;
  }
  const orderId = order.json('id');

  const payment = http.post(`${BASE}/v1/payments`, JSON.stringify({
    orderId, method: 'UPI', methodDetails: { vpa: 'soak@okaxis' },
  }), { headers: { ...headers, 'X-Idempotency-Key': receipt },
       tags: { kind: 'payment', name: 'init_payment' } });
  paymentLatency.add(payment.timings.duration);
  if (!check(payment, { 'payment 2xx': (r) => r.status >= 200 && r.status < 300 })) {
    failures.add(1);
    console.log(`PAYMENT_FAIL ${payment.status} ${String(payment.body).slice(0, 160)}`);
  }
}
