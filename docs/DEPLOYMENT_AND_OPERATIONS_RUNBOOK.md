# DineFlow Deployment, Secret Provisioning & Operations Runbook

## 1. Environment Variable Reference

| Variable | Required in Prod | Default (Dev) | Description |
| :--- | :---: | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Yes (`prod`) | `dev` | Active Spring profile (`dev`, `test`, `staging`, `prod`) |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Yes | `localhost:5432/dineflow_db` | PostgreSQL 16 connection coordinates |
| `DB_USER`, `DB_PASSWORD` | Yes | Dev placeholder | PostgreSQL credentials (inject via Secret Manager) |
| `DB_SSL_MODE` | Yes (`require`) | `prefer` | TLS mode for PostgreSQL connections |
| `JWT_SECRET` | Yes (>=32 chars) | Dev default | HMAC-SHA256 signing key (fail-fast rejected if default in prod) |
| `QR_SIGNING_SALT` | Yes (>=16 chars) | Dev default | HMAC salt for table QR tokens (fail-fast rejected if default in prod) |
| `CORS_ALLOWED_ORIGINS` | Yes (no `*`) | `http://localhost:5173` | Explicit comma-separated allowed origins |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Yes | `localhost:6379` | Redis 7 / Valkey cluster for distributed rate limiting |
| `STORAGE_TYPE` | Yes (`s3`) | `local` | Object storage backend (`local` or `s3`) |
| `S3_BUCKET`, `S3_REGION`, `S3_ENDPOINT` | Yes (if `s3`) | `dineflow-assets` | S3 / GCS / R2 / MinIO coordinates |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Yes | Placeholder | Razorpay API & webhook secrets (sandbox in staging, live in prod) |

## 2. Staging & Production Deployment Steps
1. Provision secrets in cloud Secret Manager (AWS Secrets Manager / GCP Secret Manager / Vault). Never bake secrets into Docker layers or `.env` commits.
2. Build immutable containers:
   ```bash
   docker build -t dineflow-backend:release ./backend
   docker build -t dineflow-frontend:release ./frontend
   ```
3. Run Flyway migrations (`V1`–`V18`) automatically on first replica boot (or via CI migration job) with `ddl-auto: validate`. Never edit applied migrations.
4. Verify readiness probes: `GET /actuator/health/readiness` returns `200 UP` and `/actuator/prometheus` returns `401` publicly.

## 3. PostgreSQL Backup, Restore & PITR
- Enable continuous WAL archiving (Point-in-Time Recovery with RPO <= 5 minutes) and automated daily encrypted snapshots (`pg_dump -Fc`).
- Pool sizing assumption: `DB_POOL_MAX=25` per replica x `N` replicas must stay below `max_connections - 20` on PostgreSQL (or use PgBouncer transaction pooling).
- Restore verification: restore latest snapshot into an isolated staging DB monthly and run `SELECT COUNT(*) FROM restaurant_bills, restaurant_payments;`.

## 4. Incident Rollback Runbook
1. All Sprint 6 capabilities are feature-flagged (`STORAGE_TYPE`, `RATE_LIMIT_STORE`, `dineflow.reconciliation.enabled`).
2. `V18` (`shedlock`) is strictly additive; rolling back the application container tag to the previous release requires **zero destructive schema rollback**.
3. If a migration fails mid-deploy, inspect `flyway_schema_history`, resolve the root cause, and never run `flyway clean` in staging or production.
