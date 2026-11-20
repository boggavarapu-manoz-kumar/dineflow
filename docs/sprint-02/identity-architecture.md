# DineFlow Sprint 2: Identity & Multi-Tenant Principal Architecture

## 1. Overview & Architectural Philosophy
DineFlow serves as a multi-tenant hospitality operating system. The platform bifurcates identities into two primary categories:
1. **Authenticated Staff & Platform Principals**: Identified via email/username and credentials. Governed by multi-tenant memberships, role-based access control (RBAC), and fine-grained permissions.
2. **Customer Guest Principals**: Identified via zero-login ephemeral sessions tied directly to a verified restaurant table context through signed tokens.

## 2. Multi-Tenant Membership Model
In enterprise restaurant SaaS, individuals (e.g., a regional chef, consultant, or multi-franchise manager) may belong to more than one hotel or restaurant tenant.
- A **User** has a singular global identity (`users` table).
- Access to a specific tenant is modeled as a **Membership** (`user_memberships` table).
- Each membership defines the user's role and status within that specific hotel (`HOTEL_ADMIN`, `WAITER`, `KITCHEN_STAFF`, etc.).
- **Platform Roles** (such as `SUPER_ADMIN` and `PLATFORM_SUPPORT`) are anchored globally (`tenant_id = null`) and evaluated independently of tenant memberships.

## 3. Seven Persona Experiences
1. **Super Admin**: Global oversight, tenant lifecycle administration, global subscription auditing.
2. **Hotel Owner & Hotel Admin**: Full tenant governance, staff provisioning, menu modification, table management, sales reports.
3. **Branch Manager**: Daily shift oversight, waiter dispatch management, menu item availability toggles, operational reporting.
4. **Waiter**: Table service routing, order pickup and dispatch notifications, bill settlement requests.
5. **Kitchen Staff (KDS)**: Hands-free or touch-screen preparation station, real-time ticket state progression (Received -> Preparing -> Ready).
6. **Cashier**: Order invoice generation, discount application, payment reconciliation, table checkout closure.
7. **Customer Guest**: Ephemeral zero-friction mobile web experience, menu exploration, allergen filtering, cart management, instant order placement, live status tracking, waiting mini-games.

## 4. Context Resolution & Security Flow
When an authenticated request reaches DineFlow:
1. `TenantFilter` resolves the active tenant context (`TenantContext`) from the host header, subdomain, or explicit request routing.
2. `JwtAuthenticationFilter` validates the bearer JWT.
3. The system confirms that the authenticated user possesses an **ACTIVE** membership within the resolved tenant.
4. If a user attempts to access Tenant B with a token issued for Tenant A, the request is rejected with `403 Forbidden` (`CROSS_TENANT_ACCESS_DENIED`), preventing cross-tenant data leakage.