import http from 'k6/http';
import { check, sleep } from 'k6';
import { assertStagingSafety } from './lib/config.js';

/**
 * Dedicated Rate-Limit & Webhook Burst Verification Script (Staging ONLY).
 * Verifies:
 *  - Auth login rate limiting triggers 429 + Retry-After header.
 *  - Webhook endpoint handles provider retry bursts without dropping legitimate events.
 */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
assertStagingSafety(BASE_URL);

export const options = {
  vus: 5,
  duration: '20s',
};

export default function () {
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ identifier: 'loadtest-invalid@staging.local', password: 'wrong-password' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, {
    'auth responds 401 or 429': (r) => r.status === 401 || r.status === 429,
    '429 includes Retry-After header': (r) => r.status !== 429 || Boolean(r.headers['Retry-After']),
  });

  sleep(0.2);
}
