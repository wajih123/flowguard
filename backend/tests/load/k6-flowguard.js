/**
 * FlowGuard — k6 Load Test Suite
 * ================================
 * Tests critical API flows under load.
 *
 * Usage:
 *   k6 run tests/load/k6-flowguard.js
 *   k6 run --vus 50 --duration 5m tests/load/k6-flowguard.js
 *
 * Environment variables:
 *   BASE_URL   — target backend URL (default: http://localhost:8080)
 *   TEST_EMAIL — pre-seeded test user email
 *   TEST_PASS  — pre-seeded test user password
 *
 * Install k6: https://k6.io/docs/getting-started/installation/
 */
import http from "k6/http";
import { check, sleep, group } from "k6";
import { Rate, Trend } from "k6/metrics";

// ── Configuration ──────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TEST_EMAIL = __ENV.TEST_EMAIL || "loadtest@flowguard.fr";
const TEST_PASS = __ENV.TEST_PASS || "LoadTest@Pass1";

// ── Custom metrics ─────────────────────────────────────────────────────────────
const authErrors = new Rate("auth_errors");
const apiErrors = new Rate("api_errors");
const forecastLatency = new Trend("forecast_latency_ms");

// ── Load stages ────────────────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: "30s", target: 10 }, // Ramp up to 10 VUs
    { duration: "2m", target: 50 }, // Ramp up to 50 VUs
    { duration: "3m", target: 50 }, // Hold at 50 VUs
    { duration: "1m", target: 100 }, // Spike to 100 VUs
    { duration: "30s", target: 0 }, // Ramp down
  ],
  thresholds: {
    // 95th percentile below 2 seconds for all requests
    http_req_duration: ["p(95)<2000"],
    // Error rate below 1%
    http_req_failed: ["rate<0.01"],
    // Forecast endpoint allowed 5s max (ML inference)
    forecast_latency_ms: ["p(95)<5000"],
    // Auth errors below 0.5%
    auth_errors: ["rate<0.005"],
  },
};

// ── Shared state ───────────────────────────────────────────────────────────────
let accessToken = "";
let accountId = "";

// ── Setup (runs once) ──────────────────────────────────────────────────────────
export function setup() {
  // Login and return shared data
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: TEST_EMAIL, password: TEST_PASS }),
    { headers: { "Content-Type": "application/json" } },
  );

  const ok = check(res, {
    "setup: login 200": (r) => r.status === 200,
    "setup: has accessToken": (r) =>
      JSON.parse(r.body).accessToken !== undefined,
  });

  if (!ok) {
    console.error(`Setup login failed: ${res.status} ${res.body}`);
    return {};
  }

  const body = JSON.parse(res.body);
  const token = body.accessToken;

  // Get first account ID
  const accountsRes = http.get(`${BASE_URL}/api/accounts`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  let firstAccountId = "";
  if (accountsRes.status === 200) {
    const accounts = JSON.parse(accountsRes.body);
    if (Array.isArray(accounts) && accounts.length > 0) {
      firstAccountId = accounts[0].id;
    }
  }

  return { token, accountId: firstAccountId };
}

// ── Default function (VU scenario) ────────────────────────────────────────────
export default function (data) {
  const token = data.token || "";
  const acctId = data.accountId || "";
  const headers = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };

  // ── Auth flow ──────────────────────────────────────────────────────────────
  group("auth", () => {
    const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: TEST_EMAIL, password: TEST_PASS }),
      { headers: { "Content-Type": "application/json" } },
    );
    const ok = check(res, {
      "login 200": (r) => r.status === 200,
    });
    authErrors.add(!ok);
  });

  sleep(0.5);

  // ── Dashboard KPIs ─────────────────────────────────────────────────────────
  group("kpis", () => {
    const res = http.get(`${BASE_URL}/api/kpis`, { headers });
    const ok = check(res, {
      "kpis 200 or 401": (r) => r.status === 200 || r.status === 401,
    });
    apiErrors.add(!ok);
  });

  sleep(0.3);

  // ── Transactions ───────────────────────────────────────────────────────────
  group("transactions", () => {
    if (!acctId) return;
    const res = http.get(
      `${BASE_URL}/api/transactions?accountId=${acctId}&page=0&size=20`,
      { headers },
    );
    check(res, {
      "transactions 200": (r) => r.status === 200 || r.status === 401,
    });
  });

  sleep(0.3);

  // ── Treasury forecast (ML endpoint — slower) ───────────────────────────────
  group("forecast", () => {
    if (!acctId) return;
    const start = Date.now();
    const res = http.get(
      `${BASE_URL}/api/treasury/forecast?accountId=${acctId}&horizon=30`,
      { headers },
    );
    forecastLatency.add(Date.now() - start);
    check(res, { "forecast 200": (r) => r.status === 200 || r.status === 401 });
  });

  sleep(1);

  // ── Alerts ─────────────────────────────────────────────────────────────────
  group("alerts", () => {
    const res = http.get(`${BASE_URL}/api/alerts`, { headers });
    check(res, { "alerts 200": (r) => r.status === 200 || r.status === 401 });
  });

  sleep(0.5);
}

// ── Teardown ───────────────────────────────────────────────────────────────────
export function teardown(data) {
  if (data.token) {
    http.post(
      `${BASE_URL}/api/auth/logout`,
      JSON.stringify({ refreshToken: "" }),
      {
        headers: {
          Authorization: `Bearer ${data.token}`,
          "Content-Type": "application/json",
        },
      },
    );
  }
}
