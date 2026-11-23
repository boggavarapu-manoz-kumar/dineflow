# DineFlow Security Authorization Matrix, Threat Model & Defect Register

## 1. Endpoint Authorization Matrix

| Method | Endpoint Pattern | Auth | Required Role / Permission | Tenant Scope | Branch Scope | Guest Ownership | Unauth / Denied |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: |
| `POST` | `/api/v1/auth/login`, `/refresh`, `/logout`, `/password/**` | Public | Rate-limited (`AUTH_LOGIN`) | Tenant-scoped credentials | N/A | N/A | `401` / `429` |
| `POST` | `/api/v1/customer-sessions` | Public | Valid HMAC QR token + `GUEST_SESSION` rate limit | Verified QR tenant | Verified QR branch | Creates session | `401` / `403` / `429` |
| `GET` | `/api/v1/customer/menu` | Guest JWT | `CUSTOMER_MENU_VIEW` | JWT `tenantId` | Session `branchId` | Own session | `401` / `403` |
| `GET/POST` | `/api/v1/customer/orders/**` | Guest JWT | `CUSTOMER_ORDER_VIEW_OWN` / `CUSTOMER_ORDER_CREATE` | JWT `tenantId` | Session `branchId` | `order.customerSessionId == userId` | `401` / `403` |
| `GET` | `/api/v1/customer/bills/**` | Guest JWT | `CUSTOMER_ORDER_VIEW_OWN` | JWT `tenantId` | Session `branchId` | `bill.customerSessionId == userId` | `401` / `403` |
| `GET` | `/api/v1/customer/invoices/*/pdf` | Guest JWT | `CUSTOMER_ORDER_VIEW_OWN` | JWT `tenantId` | Session `branchId` | `invoice.bill.customerSessionId == userId` | `401` / `403` |
| `GET/POST` | `/api/v1/waiter/**` | Staff JWT | `WAITER_VIEW` / `WAITER_MANAGE` | JWT `tenantId` | Staff `branchId` | N/A | `401` / `403` |
| `GET/POST` | `/api/v1/kitchen/**` | Staff JWT | `KITCHEN_VIEW` / `KITCHEN_UPDATE` | JWT `tenantId` | Staff `branchId` | N/A | `401` / `403` |
| `GET/POST` | `/api/v1/staff/bills/**` | Staff JWT | `BILL_VIEW` / `BILL_CREATE` / `BILL_VOID` | JWT `tenantId` | Staff `branchId` | N/A | `401` / `403` |
| `GET/POST` | `/api/v1/staff/invoices/**` | Staff JWT | `INVOICE_VIEW` / `INVOICE_GENERATE` / `INVOICE_CANCEL` | JWT `tenantId` | Staff `branchId` | N/A | `401` / `403` |
| `POST` | `/api/v1/payments/initiate` | Staff/Guest | `PAYMENT_PROCESS` / `BILL_COLLECT_PAYMENT` / `CUSTOMER_ORDER_VIEW_OWN` | JWT `tenantId` | Branch / Session | Guest owns bill | `401` / `403` |
| `POST` | `/api/v1/payments/staff/cash` | Staff JWT | `PAYMENT_PROCESS` / `BILL_COLLECT_PAYMENT` + `type=STAFF` | JWT `tenantId` | Staff `branchId` | Blocked for Guests | `401` / `403` |
| `POST` | `/api/v1/payments/{id}/reverse` | Staff JWT | `PAYMENT_PROCESS` / `REFUND_APPROVE` + `type=STAFF` | JWT `tenantId` | Staff `branchId` | Blocked for Guests | `401` / `403` |
| `POST` | `/api/v1/payments/webhook/{provider}` | HMAC Sig | Constant-time `X-Razorpay-Signature` verification | Resolved via persisted attempt | Persisted branch | N/A | `400` |
| `GET/POST` | `/api/v1/payments/refunds/**` | Staff JWT | `REFUND_REQUEST` / `REFUND_APPROVE` / `REFUND_EXECUTE` (dual-control > 1000) | JWT `tenantId` | Staff `branchId` | Blocked for Guests | `401` / `403` |
| `GET` | `/api/v1/reports/financial/**` | Staff JWT | `REPORT_FINANCIAL_VIEW` / `REPORT_FINANCIAL_EXPORT` | JWT `tenantId` | Staff `branchId` | Blocked for Guests | `401` / `403` |
| `GET` | `/api/v1/payments/reconciliation/locks` | Staff JWT | `PLATFORM_MANAGE` / `REPORT_FINANCIAL_VIEW` | Global ops | N/A | Blocked for non-admins | `401` / `403` |
| `GET` | `/api/v1/public/images/**` | Public | Restricted strictly to `images/` prefix + path-traversal guard | Key prefix | N/A | Never serves `documents/invoices/` | `400` / `403` / `404` |
| `WS` | `/ws` (STOMP) | JWT Header | `CONNECT` JWT + `SUBSCRIBE` tenant/branch/role/guest-ownership | JWT `tenantId` | Staff `branchId` | Guest owns order/session | `AccessDeniedException` |

---

## 2. STRIDE Threat Model Summary

| Threat | Vector | Control Implemented |
| :--- | :--- | :--- |
| **Spoofing** | Forged JWT, spoofed `X-Forwarded-For`, fake webhooks | HMAC-SHA256 JWT validation; `RateLimitingFilter` trusts `X-Forwarded-For` ONLY from `trustedProxies`; constant-time HMAC webhook signature check. |
| **Tampering** | Browser-altered prices/discounts/payment status, corrupted PDFs | Authoritative server-side bill & promotion calculation; `PaymentStateMachine` forbids downgrades; SHA-256 checksum verification on every invoice PDF download. |
| **Repudiation** | Unaudited cash receipts, reversals, manual reconciliation | Immutable `PaymentAuditRecord`, `RestaurantOrderStatusHistory`, `PaymentReconciliationLog.triggeredBy = MANUAL_STAFF:{userId}`. |
| **Information Disclosure** | Cross-tenant IDOR, stack traces, invoice path traversal, PII in Redis | Every repository query scoped by `tenantId` + branch/guest check; `GlobalExceptionHandler` strips internal exceptions; opaque UUID storage keys; SHA-256 hashed Redis rate-limit keys. |
| **Denial of Service** | Brute-force login/coupons, slow gateway exhausting HikariCP, WS floods | Distributed Redis Lua rate limiting with per-category failure policies; 3-phase non-TX gateway HTTP calls; bounded batch reconciliation (`<= 50`); `64 KB` WS message limit & `10` connections/user cap. |
| **Elevation of Privilege** | Guest invoking cash/staff APIs, kitchen subscribing to `payments` WS | `@PreAuthorize` granular permission checks + `type == STAFF` checks + `WebSocketAuthChannelInterceptor` channel-specific RBAC + separation of duties on high-value refunds. |

---

## 3. Sprint 6 Prompt 10 Security Defect Register

| ID | Severity | Affected File | Root Cause | Remediation | Status |
| :--- | :---: | :--- | :--- | :--- | :---: |
| `S6-SEC-001` | **Medium** | [RateLimitingFilter.java](file:///c:/Users/manoz/.gemini/antigravity/scratch/dineflow/backend/src/main/java/com/dineflow/security/ratelimit/RateLimitingFilter.java) | Checked `/api/v1/refunds` instead of actual `RefundController` path `/api/v1/payments/refunds`. | Matched both `/api/v1/payments/refunds` and `/api/v1/refunds` to `REFUND_REQUEST`. | **FIXED** |
| `S6-SEC-002` | **Medium** | [RateLimitingFilter.java](file:///c:/Users/manoz/.gemini/antigravity/scratch/dineflow/backend/src/main/java/com/dineflow/security/ratelimit/RateLimitingFilter.java) | Checked `/api/v1/auth/password-reset` instead of `/api/v1/auth/password/forgot` & `/reset`. | Matched `/api/v1/auth/password/` to `AUTH_LOGIN`. | **FIXED** |
| `S6-SEC-003` | **High** | [GlobalExceptionHandler.java](file:///c:/Users/manoz/.gemini/antigravity/scratch/dineflow/backend/src/main/java/com/dineflow/common/exception/GlobalExceptionHandler.java) | Catch-all `@ExceptionHandler(Exception.class)` intercepted Spring Security `AccessDeniedException` (from `@PreAuthorize`) and `HttpMessageNotReadableException`, returning `500` instead of `403` / `400`. | Added explicit handlers for `AccessDeniedException` (`403 ACCESS_DENIED`), `AuthenticationException` (`401`), and `HttpMessageNotReadableException` (`400 INVALID_REQUEST_PAYLOAD`). | **FIXED** |
| `S6-SEC-004` | **Medium** | [PaymentController.java](file:///c:/Users/manoz/.gemini/antigravity/scratch/dineflow/backend/src/main/java/com/dineflow/payment/controller/PaymentController.java) | `GET /api/v1/payments/reconciliation/locks` allowed general `PAYMENT_PROCESS` cashiers to inspect global ShedLock lock rows. | Restricted strictly to `PLATFORM_MANAGE` or `REPORT_FINANCIAL_VIEW`. | **FIXED** |

---

## 4. Residual Risk Register

| Risk ID | Description | Likelihood | Impact | Mitigation / Acceptance Condition |
| :--- | :--- | :---: | :---: | :--- |
| `RR-01` | Live Redis/Valkey Lua rate-limit & Pub/Sub fan-out integration test was skipped on offline workstation (no Docker/Redis locally). | Low | Medium | Must execute `DistributedRateLimitingAndCacheTests` against a live Redis/Valkey container in CI/staging before production cutover. |
| `RR-02` | Live AWS S3 / GCS bucket IAM & presigned URL verification requires provisioned cloud bucket credentials. | Low | Medium | Mocked SDK + local fallback verified; run staging smoke test against real bucket with Block Public Access enabled prior to go-live. |
| `RR-03` | In-memory WebSocket `SimpleBroker` + Redis Pub/Sub is ephemeral for disconnected clients. | Low | Low | Mitigated by PostgreSQL transactional outbox (`V21`) + mandatory client REST refresh on every WebSocket reconnect. |
