# DineFlow - Multi-Tenant Restaurant & Hotel Ordering SaaS Platform

> **Tagline:** Scan. Order. Enjoy.

DineFlow is a modern, commercial-grade SaaS platform enabling frictionless table ordering via QR codes, real-time kitchen orchestration (KDS), waiter dispatch, and in-browser entertainment.

---

## 5 Application Experiences
1. **Customer Web App**: Instant QR scan, zero-login guest ordering, live preparation tracking, and wait games.
2. **Hotel Admin Dashboard**: Menu management, table QR code assignment, staff roles, and analytics.
3. **Kitchen Display System (KDS)**: Touchscreen ticket dispatch with preparation urgency indicators.
4. **Waiter Handheld App**: Real-time ready alerts, table routing, and bill settlement.
5. **SaaS Super Admin**: Platform oversight, multi-tenant billing tiers, and system telemetry.

---

## Technology Stack
- **Backend**: Java 25 LTS / 21+, Spring Boot 4.x, Spring Web, Spring Data JPA, Hibernate 7, Spring Security 7, Flyway, PostgreSQL, Maven Wrapper, JUnit 5.
- **Frontend**: React 19, TypeScript (Strict), Vite, Tailwind CSS v4, TanStack Query, React Hook Form, Zod, Lucide React, Vitest.
- **Infrastructure**: Docker, Docker Compose, Git, Environment-based configuration.

---

## Sprint 2: Identity & Security Foundation

DineFlow Sprint 2 provides an enterprise multi-tenant identity and access management system:
- **Zero-Trust Multi-Tenancy**: Data isolation with tenant discriminator columns, ThreadLocal `TenantContext`, and cross-tenant access rejection.
- **Rotating Refresh Token Families**: Opaque refresh tokens stored in `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookies. SHA-256 hashed persistence with automatic token reuse detection (revokes entire token family upon replay attempt).
- **In-Memory JWT Access Tokens**: 15-minute validity stored in frontend memory with silent background refresh via TanStack Query.
- **Role-Based & Permission-Based Access Control (RBAC)**: Fine-grained permissions evaluated via method security (`@PreAuthorize`) and SpEL (`@tenantSecurity.hasTenantAccess(#tenantId)`).
- **Passwordless Customer Table Entry**: Dynamic HMAC-SHA256 entry tokens embedded into QR codes, time-boxed guest sessions (4 hours), and GDPR/DPDP-compliant phone masking (`+91****10`).
- **Audit Logging & Throttling**: Brute-force protection via token-bucket rate limiting and audit logging to `login_audit_logs`.

---

## Quick Start & Local Development

### 1. Environment Configuration

Copy the example environment configurations:
```bash
# Backend Environment (Optional overrides)
SPRING_PROFILES_ACTIVE=local
PORT=8080
DB_HOST=localhost
DB_PORT=5432
DB_NAME=dineflow_db
DB_USER=dineflow_admin
DB_PASSWORD=dineflow_secure_password
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
QR_SIGNING_SALT=dineflow-qr-salt-production-value-super-secure-change-in-prod
```

### 2. Infrastructure Setup (PostgreSQL)

Launch the containerized PostgreSQL database:
```bash
docker compose up -d postgres
```
Flyway automatically executes database migrations (`V1__init_tenants_and_core_schema.sql` and `V2__identity_and_rbac.sql`) upon application launch.

### 3. Backend Service Startup

```bash
cd backend
# Windows:
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# Linux / macOS:
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
Verify the health endpoint:
```bash
curl http://localhost:8080/api/v1/health
```

### 4. Frontend Application Startup

```bash
cd frontend
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173) in your browser.

---

## Authentication Endpoints & Testing

### 1. Staff Authentication
- **Endpoint**: `POST /api/v1/auth/login`
- **Payload**:
```json
{
  "email": "owner@grandheritage.com",
  "password": "SecurePassword123!",
  "tenantId": "e4a8b792-6632-4d2c-9be2-4410a51c9d2f"
}
```
*Note: For Super Admin, `tenantId` is omitted or `null`.*

### 2. Session Refresh
- **Endpoint**: `POST /api/v1/auth/refresh`
- **Cookie**: Automatic via browser `__Host-DineFlow-RT` cookie (`HttpOnly; Secure; SameSite=Strict`).

### 3. Customer Table Entry (Passwordless)
- **Endpoint**: `POST /api/v1/auth/customer-sessions`
- **Payload**:
```json
{
  "customerName": "Rahul Sharma",
  "mobileNumber": "+919876543210",
  "signedEntryToken": "<base64_hmac_token_from_table_qr>"
}
```

---

## Automated Test Verification

Execute all regression and security test suites:

- **Backend Unit & Security Integration Tests (12 tests)**:
```bash
cd backend
.\mvnw.cmd test
```
- **Frontend Unit & Component Tests (7 tests)**:
```bash
cd frontend
npx vitest run
```
- **Frontend Production Build**:
```bash
cd frontend
npm run build
```

---

## Architecture & Sprint Documentation

Detailed documentation is available in the [`docs/`](./docs) directory:
- [Sprint 2 Security Audit Report](./docs/sprint-02/security-audit-report.md)
- [Sprint 2 Identity Architecture](./docs/sprint-02/identity-architecture.md)
- [Sprint 2 Role & Permission Matrix](./docs/sprint-02/role-permission-matrix.md)
- [Sprint 2 Database Schema & ERD](./docs/sprint-02/database-design.md)
- [Sprint 2 API Contracts](./docs/sprint-02/api-contracts.md)
- [Sprint 2 Security Design](./docs/sprint-02/security-design.md)
- [Sprint 2 Authorization Model](./docs/sprint-02/authorization-model.md)
