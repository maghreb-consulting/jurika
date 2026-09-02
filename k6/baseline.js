// JURIKA — k6 baseline load test (Sprint 14 ter F6).
//
// 3 scenarios paralleles : login (100 VUs), dashboard (50 VUs), upload (20 VUs)
// Pas de gate bloquant V1 — la baseline sert de reference pour Sprint 15.
//
// Lancement local (k6 installe) :
//   k6 run -e BASE_URL=https://staging.jurika.ma \
//          -e SMOKE_EMAIL=smoke-test@maghreb-consulting.ma \
//          -e SMOKE_PASSWORD=<...> \
//          -e WORKSPACE_CODE=SMOKE-TEST-WORKSPACE \
//          k6/baseline.js
//
// CI : workflow .github/workflows/perf-baseline.yml (cron mensuel, declenche manuel).
// Resultats agreges et stockes dans docs/v2/PERF_BASELINE_SPRINT_14_TER.md.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://staging.jurika.ma';
const SMOKE_EMAIL = __ENV.SMOKE_EMAIL || 'smoke-test@maghreb-consulting.ma';
const SMOKE_PASSWORD = __ENV.SMOKE_PASSWORD || '';
const WORKSPACE_CODE = __ENV.WORKSPACE_CODE || 'SMOKE-TEST-WORKSPACE';

const loginDuration = new Trend('login_duration_ms');
const dashboardDuration = new Trend('dashboard_duration_ms');
const uploadDuration = new Trend('upload_duration_ms');
const businessErrors = new Counter('business_errors_total');

export const options = {
  scenarios: {
    login: {
      executor: 'constant-vus',
      vus: 100,
      duration: '5m',
      exec: 'loginScenario',
      tags: { scenario: 'login' },
    },
    dashboard: {
      executor: 'constant-vus',
      vus: 50,
      duration: '5m',
      exec: 'dashboardScenario',
      tags: { scenario: 'dashboard' },
      startTime: '10s',
    },
    upload: {
      executor: 'constant-vus',
      vus: 20,
      duration: '5m',
      exec: 'uploadScenario',
      tags: { scenario: 'upload' },
      startTime: '20s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<2000'],
    http_req_failed: ['rate<0.01'],
    'login_duration_ms{scenario:login}': ['p(95)<1000'],
    'dashboard_duration_ms{scenario:dashboard}': ['p(95)<800'],
    'upload_duration_ms{scenario:upload}': ['p(95)<3000'],
    business_errors_total: ['count<50'],
  },
  summaryTrendStats: ['min', 'med', 'avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

function login() {
  const r = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ workspaceCode: WORKSPACE_CODE, email: SMOKE_EMAIL, password: SMOKE_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /auth/login' } },
  );
  loginDuration.add(r.timings.duration);
  const ok = check(r, { 'login 200': (resp) => resp.status === 200 });
  if (!ok) businessErrors.add(1);
  return ok ? r.json('accessToken') : null;
}

export function loginScenario() {
  login();
  sleep(1);
}

export function dashboardScenario() {
  const token = login();
  if (!token) return;
  const r = http.get(`${BASE_URL}/api/v1/dashboard/superviseur`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET /dashboard/superviseur' },
  });
  dashboardDuration.add(r.timings.duration);
  const ok = check(r, { 'dashboard 200': (resp) => resp.status === 200 });
  if (!ok) businessErrors.add(1);
  sleep(2);
}

export function uploadScenario() {
  const token = login();
  if (!token) return;
  // Upload simule via OPTIONS / HEAD pour ne pas saturer MinIO en staging
  const r = http.options(`${BASE_URL}/api/v1/dataroom/fiscal/upload`, null, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'OPTIONS /dataroom/fiscal/upload' },
  });
  uploadDuration.add(r.timings.duration);
  const ok = check(r, { 'upload preflight 200-204': (resp) => resp.status >= 200 && resp.status < 300 });
  if (!ok) businessErrors.add(1);
  sleep(3);
}

export function handleSummary(data) {
  return {
    'k6-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data),
  };
}

function textSummary(data) {
  const m = data.metrics;
  const fmt = (v) => (v ? v.toFixed(0) : 'n/a');
  return `
═══ JURIKA k6 baseline (Sprint 14 ter F6) ═══
http_req_duration       p95=${fmt(m.http_req_duration.values['p(95)'])}ms  p99=${fmt(m.http_req_duration.values['p(99)'])}ms
login_duration_ms       p95=${fmt(m.login_duration_ms?.values['p(95)'])}ms
dashboard_duration_ms   p95=${fmt(m.dashboard_duration_ms?.values['p(95)'])}ms
upload_duration_ms      p95=${fmt(m.upload_duration_ms?.values['p(95)'])}ms
http_req_failed rate    ${(m.http_req_failed.values.rate * 100).toFixed(2)}%
business_errors_total   ${m.business_errors_total?.values.count ?? 0}
`;
}
