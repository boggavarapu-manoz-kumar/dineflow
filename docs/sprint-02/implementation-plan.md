# DineFlow Sprint 2: Step-by-Step Implementation Plan

## Phase 1: Database Migration & Schema Foundations
- [ ] Create Flyway migration `V2__identity_and_rbac.sql` defining `users`, `roles`, `permissions`, `role_permissions`, `user_memberships`, `refresh_sessions`, `guest_sessions`, and `login_audit_logs`.
- [ ] Seed initial roles (`SUPER_ADMIN`, `HOTEL_OWNER`, `HOTEL_ADMIN`, `BRANCH_MANAGER`, `WAITER`, `KITCHEN_STAFF`, `CASHIER`, `CUSTOMER_GUEST`) and system permissions.

## Phase 2: Backend Security Engine & Token Utilities
- [ ] Implement `JwtTokenService`: Token creation, verification, claim extraction, and tenant scope enforcement.
- [ ] Implement `RefreshTokenService`: CSPRNG token generation, SHA-256 hashing, family tracking, and reuse detection.
- [ ] Implement `PasswordEncoderConfig`: BCrypt work factor 12.
- [ ] Implement `JwtAuthenticationFilter`: Extract Bearer token, validate subject and tenant scope, and populate `SecurityContextHolder`.

## Phase 3: Identity Domain Entities & Repositories
- [ ] Create JPA entities: `User`, `Role`, `Permission`, `UserMembership`, `RefreshSession`, `GuestSession`, `LoginAuditLog`.
- [ ] Create Spring Data JPA repositories with strict tenant filtering queries (`findByUserIdAndTenantId`).

## Phase 4: Authentication Controllers & Endpoints
- [ ] Implement `AuthController`: `/login`, `/refresh`, `/logout`, `/logout-all`, `/me`.
- [ ] Implement `GuestAuthController`: `/entry`, `/session`.
- [ ] Update `GlobalExceptionHandler` with `BadCredentialsException`, `AccountLockedException`, `CrossTenantAccessException`.

## Phase 5: Frontend Authentication & Route Guards
- [ ] Implement `AuthContext.tsx`: In-memory access token state, silent refresh interceptor, user identity provider.
- [ ] Implement `<ProtectedRoute>` and `<RoleGuard>` components.
- [ ] Implement Staff Login View (`/staff/login`), Super Admin Login (`/admin/login`), and Guest QR Entry Modal (`/table/:slug/:tableNumber`).

## Phase 6: Automated Verification & Testing
- [ ] Backend tests: JWT parsing, token expiration, refresh rotation, cross-tenant rejection, BCrypt hashing.
- [ ] Frontend tests: Auth state provider, role guard redirection, protected route access.
- [ ] End-to-end acceptance verification: Staff login, token refresh, and guest table entry.