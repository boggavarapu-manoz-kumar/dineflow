# DineFlow Multi-Tenant Security & Isolation Architecture

## 1. Zero Trust Cross-Tenant Enforcement
DineFlow does not trust client-supplied tenant headers or path variables.
1. `TenantFilter` resolves the active tenant identity from validated host headers or trusted tokens.
2. `JwtAuthenticationFilter` matches the token's embedded `tenantId` claim against `TenantContext`.
3. If an attacker holding a valid token from Hotel A requests a resource belonging to Hotel B, the request is blocked with `403 Forbidden` (`CROSS_TENANT_ACCESS_DENIED`).

## 2. Platform Support vs Hotel Boundary
Platform Support personnel hold platform-level analytical permissions (`PLATFORM_ANALYTICS_VIEW`), but cannot access confidential dining table orders, invoices, or customer data without explicit time-bound elevated access logged in `login_audit_logs`.