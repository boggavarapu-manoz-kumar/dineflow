# DineFlow Performance, k6 Load Testing & Bottleneck Analysis Guide

> **Honesty & Safety Notice**: Actual k6 load tests have **NOT** been executed against a live staging cluster on this offline developer workstation (`k6`, Docker, and staging PostgreSQL/Redis are not running locally). No latency or production capacity numbers are fabricated below. All scripts and templates are ready for immediate execution once the isolated staging environment is provisioned.

---

## 1. Safety Rules

1. **Isolated Staging Only**: Every k6 script enforces `ALLOW_LOAD_TEST=true` and rejects production-like URLs (`prod.`, `production`, `api.dineflow.com`).
2. **Synthetic Data Only**: Seed at least 2 synthetic tenants (`Tenant A`, `Tenant B`), 2 branches each, 50 menu items, 20 staff accounts, and sandbox/test Razorpay credentials.
3. **Never Trigger Live Payments/Refunds**: Use `MOCK` or Razorpay Test Mode keys only.

---

## 2. Load Profiles & Terminology

| Profile (`-e PROFILE=...`) | Virtual Users (VUs) | Duration | Purpose |
| :--- | :---: | :---: | :--- |
| `baseline` | 5 VUs | 1m 15s | Baseline p50/p95/p99 latency & error-free sanity check. |
| `normal` | 25 VUs | 7m | Expected multi-tenant lunch service traffic. |
| `peak` | Ramp 50 -> 100 VUs | 10m | Peak Friday dinner rush across dining tables & KDS. |
| `burst` | Spike 10 -> 150 VUs | 1m 50s | Sudden QR scan burst / tournament event wave. |
| `sustained` | 60 VUs | 34m | Soak test for HikariCP leaks, JVM GC pauses, and Redis memory growth. |
| `recovery` | 40 VUs | 10m | Steady traffic during controlled chaos/failure drills. |

*(Distinguish: **VUs** = concurrent simulated sessions; **RPS** = measured HTTP requests/sec; **WS VUs** = concurrent WebSocket connections; **Registered Accounts** = total rows in DB.)*

---

## 3. Execution Commands

```bash
# 1. Baseline multi-tenant operational + WebSocket test
k6 run -e ALLOW_LOAD_TEST=true -e PROFILE=baseline \
  -e BASE_URL=https://staging.dineflow.example.com \
  -e TENANT_A_STAFF_JWT="$TENANT_A_STAFF_JWT" \
  -e TENANT_B_STAFF_JWT="$TENANT_B_STAFF_JWT" \
  -e TENANT_A_GUEST_JWT="$TENANT_A_GUEST_JWT" \
  -e TENANT_A_BILL_ID="$TENANT_A_BILL_ID" \
  --summary-export=load-tests/results/baseline-summary.json \
  load-tests/k6-dineflow-staging.js

# 2. Rate-limit 429 & Retry-After verification
k6 run -e ALLOW_LOAD_TEST=true -e BASE_URL=https://staging.dineflow.example.com \
  load-tests/k6-rate-limit-and-failure.js
```

---

## 4. Controlled Failure-Injection Drills (Staging)

| Drill | Injection Method | Expected System Behavior |
| :--- | :--- | :--- |
| **Redis Temporarily Unavailable** | Stop staging Redis container for 60s during `recovery` profile. | `PUBLIC_MENU` & `WEBHOOK` fail open; `AUTH_LOGIN`/`PAYMENT_INITIATE` enforce `LOCAL_FALLBACK`; readiness probe stays `UP`; automatic recovery when Redis restarts. |
| **Payment Provider Timeout** | Point staging Razorpay base URL to fault-injection stub with 12s delay. | HikariCP pool stays healthy (Phase B runs outside DB TX); attempts transition to `PENDING_CONFIRMATION` (never `FAILED`); ShedLock reconciler recovers them. |
| **Application Replica Restart** | Rolling restart 1 of 2 staging pods during `normal` profile. | Graceful shutdown drains in-flight requests (30s); WebSocket clients auto-reconnect and refresh REST state; ShedLock transfers jobs cleanly. |

---

## 5. Bottleneck Investigation Checklist

1. **Database Queries**: Run PostgreSQL `pg_stat_statements` top-10 by `total_exec_time` + `EXPLAIN (ANALYZE, BUFFERS)` on slow queries; check for N+1 fetches in `CustomerMenuService` / `OrderService`.
2. **Connection Pool**: Monitor `hikaricp_connections_active` / `hikaricp_connections_pending` in Grafana (`grafana-dineflow-overview.json`).
3. **Cache Effectiveness**: Verify `dineflow_cache_requests_total{cache="public_menu", outcome="hit"}` > 85% under `normal`/`peak` profiles.
4. **JVM & GC**: Inspect `jvm_gc_pause_seconds_max` and `jvm_memory_used_bytes` via `/actuator/prometheus`.

---

## 6. Staging Performance Sign-Off Report Template

| Field | Recorded Value |
| :--- | :--- |
| **Status** | `PENDING ISOLATED STAGING EXECUTION` |
| **Application Version / Git SHA** | *(fill on staging run)* |
| **Infrastructure (Pods / CPU / RAM / Postgres / Redis)** | *(fill on staging run)* |
| **Dataset Size (Tenants / Branches / Items / Orders)** | *(fill on staging run)* |
| **Profile / VUs / Duration / Measured RPS** | *(fill on staging run)* |
| **HTTP Latency (p50 / p90 / p95 / p99)** | *(fill on staging run)* |
| **HTTP Error Rate & 429 Rate** | *(fill on staging run)* |
| **Multi-Tenant Isolation Violations (`dineflow_tenant_isolation_violations`)** | Must be `0.00%` |
| **Identified Bottlenecks & Retest Evidence** | *(attach `baseline-summary.json` / `peak-summary.json`)* |
| **Verified Safe Capacity Envelope** | *(sign off only after measured staging results)* |
