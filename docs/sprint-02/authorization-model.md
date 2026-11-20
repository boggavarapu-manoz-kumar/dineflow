# DineFlow Advanced Authorization Model

## 1. Principle of Least Privilege
DineFlow enforces permission-based authorization over crude role strings. Every authenticated principal is issued granular authority strings derived strictly from persisted database bindings.

### Full Permission Hierarchy
- **Platform Governance**: `PLATFORM_MANAGE`, `TENANT_VIEW`, `TENANT_MANAGE`, `PLATFORM_ANALYTICS_VIEW`
- **Hotel Operations**: `HOTEL_VIEW`, `HOTEL_MANAGE`, `SUBSCRIPTION_MANAGE`, `ANALYTICS_VIEW`
- **Staff Administration**: `STAFF_VIEW`, `STAFF_CREATE`, `STAFF_UPDATE`
- **Menu Management**: `MENU_VIEW`, `MENU_MANAGE`
- **Dining Floor & Branches**: `BRANCH_VIEW`, `WAITER_ASSIGN`
- **Orders**: `ORDER_VIEW`, `ORDER_MANAGE`
- **Cashier Desk**: `BILL_VIEW`, `BILL_MANAGE`, `PAYMENT_VIEW`
- **Waitstaff**: `WAITER_VIEW`, `SERVICE_REQUEST_VIEW`, `SERVICE_REQUEST_UPDATE`
- **Kitchen Display System**: `KITCHEN_VIEW`, `KITCHEN_UPDATE`
- **Customer Guest Dining**: `CUSTOMER_MENU_VIEW`, `CUSTOMER_ORDER_CREATE`, `CUSTOMER_ORDER_VIEW_OWN`, `CUSTOMER_SERVICE_REQUEST_CREATE`

## 2. Reusable Authorization Evaluators
- `@PreAuthorize("hasAuthority('STAFF_CREATE')")`: Verifies user holds specific authority.
- `@PreAuthorize("@tenantSecurity.hasTenantAccess(#tenantId)")`: Enforces strict tenant isolation, guaranteeing Hotel A staff cannot inspect Hotel B records.
- `@PreAuthorize("@tenantSecurity.isGuestSessionOwner(#sessionId)")`: Enforces customer session ownership, preventing Customer A from inspecting Customer B's order items.