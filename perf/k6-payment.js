import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Usage:
//   k6 run -e BASE_URL=http://localhost:8080 -e KEY_ID=... -e KEY_SECRET=... perf/k6-payment.js
//
// Thresholds fail the run when the payment path regresses.

export const options = {
  scenarios: {
    payment_initiate: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '60s', target: 100 },
        { duration: '30s', target: 100 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<800'],
    'http_req_duration{name:create_order}': ['p(95)<300'],
    'http_req_duration{name:init_payment}': ['p(99)<800'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KEY_ID = __ENV.KEY_ID;
const KEY_SECRET = __ENV.KEY_SECRET;

const auth = 'Basic ' + encoding.b64encode(`${KEY_ID}:${KEY_SECRET}`);

export default function () {
  const receipt = `k6-${Date.now()}-${randomString(8)}`;

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
    'order created': (r) => r.status === 201 || r.status === 200,
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
    'payment accepted': (r) => r.status === 200 || r.status === 201,
    'payment has status': (r) => r.json('status') !== undefined,
  });
}
