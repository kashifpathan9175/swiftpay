import http from 'k6/http';
import { check } from 'k6';

const baseUrl =
    __ENV.BASE_URL || 'http://127.0.0.1:59728';

const rate = __ENV.RATE ? Number(__ENV.RATE) : 25;
const duration = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    payments: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 25),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: 50,
      maxVUs: 300,
    },
  },

  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {

  const payload = {
    idempotency_key:
        `k6-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,

    sender_id: 1001,
    receiver_id: 1002,

    amount: (Math.random() * 100 + 5).toFixed(2),

    currency: 'USD',
  };

  const res = http.post(
      `${baseUrl}/v1/payments`,
      JSON.stringify(payload),
      {
        headers: {
          'Content-Type': 'application/json',
        },
      }
  );

  check(res, {
    'request succeeded': (r) =>
        r.status === 200 ||
        r.status === 201 ||
        r.status === 202,

    'response contains payment data': (r) => {
      const body = r.body || '';

      return body.includes('transactionId') ||
          body.includes('transaction_id') ||
          body.includes('status');
    },
  });
}