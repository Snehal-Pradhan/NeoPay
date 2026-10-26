import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';

// End-to-end payment benchmark.
//
// Usage:
//   k6 run -e BASE_URL=http://localhost:8080 \
//          -e AUTH_POOL=<base64(keyId:keySecret)>,<...> \
//          perf/k6-payment.js
//
// AUTH_POOL is a comma-separated list of pre-encoded Basic credentials. A single
// KEY_ID/KEY_SECRET pair still works as a fallback for a quick smoke run.
// The default scenario is a constant arrival rate of 15 order+payment flows per
// second (30 HTTP requests per second). Independent keys are used so the gateway
// per-key limit of 200 requests/minute is never approached.

export const options = {
  scenarios: {
    payment_flow: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 15),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 25,
      maxVUs: 60,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:create_order}': ['p(95)<250'],
    'http_req_duration{name:init_payment}': ['p(95)<350', 'p(99)<600'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_POOL = (__ENV.AUTH_POOL || '')
  .split(',')
  .map((value) => value.trim())
  .filter((value) => value.length > 0);

const FALLBACK_AUTH =
  __ENV.KEY_ID && __ENV.KEY_SECRET
    ? 'Basic ' + encoding.b64encode(`${__ENV.KEY_ID}:${__ENV.KEY_SECRET}`)
    : null;

function pickAuth() {
  if (AUTH_POOL.length > 0) {
    return 'Basic ' + AUTH_POOL[Math.floor(Math.random() * AUTH_POOL.length)];
  }
  return FALLBACK_AUTH;
}

export default function () {
  const auth = pickAuth();
  if (!auth) {
    throw new Error('AUTH_POOL or KEY_ID/KEY_SECRET must be provided');
  }

  const receipt = `k6-${__VU}-${__ITER}-${Date.now()}`;

  const order = http.post(
    `${BASE_URL}/v1/orders`,
    JSON.stringify({
      amount: { amountUnits: 5000, currency: 'INR' },
      receipt,
    }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: auth },
      tags: { name: 'create_order' },
    },
  );

  const orderOk = check(order, {
    'order created (2xx)': (r) => r.status === 200 || r.status === 201,
  });
  if (!orderOk) return;

  const orderId = order.json('id');

  const payment = http.post(
    `${BASE_URL}/v1/payments`,
    JSON.stringify({
      orderId,
      method: 'UPI',
      methodDetails: { vpa: 'success@okaxis' },
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: auth,
        'X-Idempotency-Key': `${receipt}-pay`,
      },
      tags: { name: 'init_payment' },
    },
  );

  check(payment, {
    'payment accepted (2xx)': (r) => r.status === 200 || r.status === 201,
    'payment status present': (r) => r.json('status') !== undefined,
  });
}
