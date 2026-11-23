import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep, group } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { assertStagingSafety, buildStagesForProfile } from './lib/config.js';

/**
 * DineFlow Multi-Tenant Staging Load Test Suite (k6)
 *
 * Usage (Isolated Staging ONLY):
 *   k6 run -e ALLOW_LOAD_TEST=true -e PROFILE=baseline \
 *     -e BASE_URL=https://staging.dineflow.example.com \
 *     -e TENANT_A_STAFF_JWT=... -e TENANT_B_STAFF_JWT=... \
 *     -e TENANT_A_GUEST_JWT=... -e TENANT_A_BILL_ID=... \
 *     load-tests/k6-dineflow-staging.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || BASE_URL.replace(/^http/, 'ws') + '/ws';
const PROFILE = __ENV.PROFILE || 'baseline';

assertStagingSafety(BASE_URL);

const menuLatency = new Trend('dineflow_menu_latency', true);
const kitchenLatency = new Trend('dineflow_kitchen_latency', true);
const financialLatency = new Trend('dineflow_financial_latency', true);
const wsConnectLatency = new Trend('dineflow_ws_connect_latency', true);
const isolationViolations = new Rate('dineflow_tenant_isolation_violations');
const rateLimitedCount = new Counter('dineflow_rate_limited_429_total');

export const options = {
  scenarios: {
    customer_and_staff_traffic: {
      executor: 'ramping-vus',
      stages: buildStagesForProfile(PROFILE),
      exec: 'mixedOperationalFlow',
    },
    websocket_presence: {
      executor: 'constant-vus',
      vus: Number(__ENV.WS_VUS || 5),
      duration: __ENV.WS_DURATION || '1m',
      exec: 'websocketHandshakeFlow',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1200'],
    http_req_failed: ['rate<0.01'],
    dineflow_menu_latency: ['p(95)<250'],
    dineflow_kitchen_latency: ['p(95)<350'],
    dineflow_financial_latency: ['p(95)<600'],
    dineflow_tenant_isolation_violations: ['rate==0'],
  },
};

const TENANT_A_STAFF_JWT = __ENV.TENANT_A_STAFF_JWT || '';
const TENANT_B_STAFF_JWT = __ENV.TENANT_B_STAFF_JWT || '';
const TENANT_A_GUEST_JWT = __ENV.TENANT_A_GUEST_JWT || '';
const TENANT_A_BILL_ID = __ENV.TENANT_A_BILL_ID || '';

export function mixedOperationalFlow() {
  // 1. Health & Correlation ID check
  group('health_probe', () => {
    const res = http.get(`${BASE_URL}/api/v1/health`);
    check(res, {
      'health 200': (r) => r.status === 200,
      'correlation header present': (r) => Boolean(r.headers['X-Correlation-Id'] || r.headers['X-Correlation-ID']),
    });
  });

  // 2. Customer Menu Browsing (exercises Redis versioned menu cache)
  if (TENANT_A_GUEST_JWT) {
    group('customer_menu_and_orders', () => {
      const headers = { Authorization: `Bearer ${TENANT_A_GUEST_JWT}` };
      const menuRes = http.get(`${BASE_URL}/api/v1/customer/menu`, { headers });
      menuLatency.add(menuRes.timings.duration);
      if (menuRes.status === 429) rateLimitedCount.add(1);
      check(menuRes, { 'customer menu 200': (r) => r.status === 200 });

      const ordersRes = http.get(`${BASE_URL}/api/v1/customer/orders`, { headers });
      check(ordersRes, { 'customer own orders 200': (r) => r.status === 200 });
    });
  }

  // 3. Staff Operations & Financial Queries (Tenant A)
  if (TENANT_A_STAFF_JWT) {
    group('staff_and_financial_ops', () => {
      const headers = { Authorization: `Bearer ${TENANT_A_STAFF_JWT}` };

      const kdsRes = http.get(`${BASE_URL}/api/v1/kitchen/queue`, { headers });
      kitchenLatency.add(kdsRes.timings.duration);
      check(kdsRes, { 'kitchen queue 200 or 403': (r) => r.status === 200 || r.status === 403 });

      const waiterRes = http.get(`${BASE_URL}/api/v1/waiter/orders`, { headers });
      check(waiterRes, { 'waiter orders 200 or 403': (r) => r.status === 200 || r.status === 403 });

      const reportRes = http.get(`${BASE_URL}/api/v1/reports/financial?period=DAILY&timezone=Asia/Kolkata`, { headers });
      financialLatency.add(reportRes.timings.duration);
      check(reportRes, { 'financial report 200': (r) => r.status === 200 });

      const billsRes = http.get(`${BASE_URL}/api/v1/staff/bills`, { headers });
      check(billsRes, { 'staff bills 200': (r) => r.status === 200 });
    });
  }

  // 4. Multi-Tenant Isolation Under Load (Tenant B MUST NEVER read Tenant A's bill)
  if (TENANT_B_STAFF_JWT && TENANT_A_BILL_ID) {
    group('multi_tenant_isolation_check', () => {
      const headers = { Authorization: `Bearer ${TENANT_B_STAFF_JWT}` };
      const crossRes = http.get(`${BASE_URL}/api/v1/staff/bills/${TENANT_A_BILL_ID}`, { headers });
      const isolated = crossRes.status === 403 || crossRes.status === 404;
      isolationViolations.add(!isolated);
      check(crossRes, { 'cross-tenant bill strictly blocked': () => isolated });
    });
  }

  sleep(1);
}

export function websocketHandshakeFlow() {
  if (!TENANT_A_STAFF_JWT) {
    sleep(2);
    return;
  }
  const start = Date.now();
  const res = ws.connect(WS_URL, {}, (socket) => {
    socket.on('open', () => {
      wsConnectLatency.add(Date.now() - start);
      const connectFrame = `CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer ${TENANT_A_STAFF_JWT}\n\n\0`;
      socket.send(connectFrame);
    });
    socket.on('message', (msg) => {
      if (msg.startsWith('CONNECTED')) {
        socket.send('DISCONNECT\n\n\0');
        socket.close();
      }
    });
    socket.setTimeout(() => socket.close(), 5000);
  });
  check(res, { 'ws upgrade 101': (r) => r && r.status === 101 });
  sleep(2);
}
