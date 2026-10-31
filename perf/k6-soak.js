// Soak profile: ramp to a sustained rate and hold it long enough for pools, GC and
// connection counts to stabilise. Same flow as k6-payment.js; different load shape.
//
// Env: BASE_URL, AUTH_LIST (comma-separated keyId:keySecret), PEAK_RATE, HOLD.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PEAK = Number(__ENV.PEAK_RATE || 100);
const HOLD = __ENV.HOLD || '15m';
const AUTH = (__ENV.AUTH_LIST || '').split(',').filter(Boolean).map((pair) => {
  const [keyId, keySecret] = pair.split(':');
  return { keyId, keySecret };
});

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
    'http_req_duration{kind:order}': ['p(99)<400'],
    'http_req_duration{kind:payment}': ['p(99)<400'],
  },
};

const failures = new Counter('flow_failures');
const orderLatency = new Trend('order_latency', true);
const paymentLatency = new Trend('payment_latency', true);

export default function () {
  if (AUTH.length === 0) throw new Error('AUTH_LIST is empty');
  const creds = AUTH[__VU % AUTH.length];
  const headers = {
    'Content-Type': 'application/json',
    'X-Key-Id': creds.keyId,
    'X-Key-Secret': creds.keySecret,
  };
  const receipt = `soak-${__VU}-${__ITER}-${Date.now()}`;

  const order = http.post(`${BASE}/v1/orders`, JSON.stringify({
    amountUnits: 2500, currency: 'INR', receipt,
  }), { headers, tags: { kind: 'order' } });
  orderLatency.add(order.timings.duration);
  if (!check(order, { 'order 2xx': (r) => r.status >= 200 && r.status < 300 })) {
    failures.add(1);
    return;
  }
  const orderId = order.json('id');

  const payment = http.post(`${BASE}/v1/payments`, JSON.stringify({
    orderId, method: 'UPI', methodDetails: { vpa: 'soak@okaxis' },
  }), { headers: { ...headers, 'X-Idempotency-Key': receipt }, tags: { kind: 'payment' } });
  paymentLatency.add(payment.timings.duration);
  if (!check(payment, { 'payment 2xx': (r) => r.status >= 200 && r.status < 300 })) {
    failures.add(1);
  }
}
