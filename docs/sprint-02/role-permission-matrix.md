# DineFlow Sprint 2: Role & Permission Matrix

## 1. Permission Granularity
Permissions follow domain-scoped verb naming (`domain:action`).

| Permission Code | Description |
|---|---|
| `platform:tenants:create` | Provision new hotel tenant subscriptions |
| `platform:tenants:manage` | Modify subscription tier, suspend/activate tenants |
| `platform:metrics:view` | View cross-tenant operational metrics |
| `tenant:settings:view` | View hotel profile and operating configurations |
| `tenant:settings:edit` | Modify branding, taxes, currencies, service charges |
| `staff:users:view` | View roster of staff members in hotel |
| `staff:users:manage` | Invite, assign roles, and revoke staff memberships |
| `menu:catalog:view` | Browse food items and categories |
| `menu:catalog:edit` | Create/edit categories, prices, modifiers, photos |
| `menu:item:toggle_stock` | Toggle item 86/out-of-stock in real-time |
| `tables:layout:manage` | Configure dining sections, tables, and generate QR codes |
| `orders:view` | View incoming and historical orders |
| `orders:create` | Place new dine-in table orders |
| `orders:status:update` | Update order progression (Preparing, Ready, Served) |
| `orders:cancel` | Cancel order with mandatory reason code |
| `billing:invoice:create` | Generate customer bill, apply service charge/taxes |
| `billing:payment:process`| Mark bill as settled (Cash, UPI, Card) |
| `kds:display:view` | Live interactive kitchen display board |
| `kds:ticket:bump` | Mark food preparation tickets ready |
| `waiter:dispatch:view` | View ready dish alerts assigned to tables |
| `waiter:dispatch:serve` | Confirm delivery of dishes to guest tables |

## 2. Role-to-Permission Mapping

| Role | Applicable Permissions |
|---|---|
| **SUPER_ADMIN** | `platform:tenants:create`, `platform:tenants:manage`, `platform:metrics:view`, `tenant:settings:view` |
| **PLATFORM_SUPPORT**| `platform:metrics:view`, `tenant:settings:view` |
| **HOTEL_OWNER** | All `tenant:*`, `staff:*`, `menu:*`, `tables:*`, `orders:*`, `billing:*`, `kds:*`, `waiter:*` |
| **HOTEL_ADMIN** | All `tenant:settings:view`, `staff:*`, `menu:*`, `tables:*`, `orders:*`, `billing:*`, `kds:*`, `waiter:*` |
| **BRANCH_MANAGER**| `tenant:settings:view`, `staff:users:view`, `menu:catalog:view`, `menu:item:toggle_stock`, `tables:layout:manage`, `orders:*`, `billing:*`, `kds:*`, `waiter:*` |
| **CASHIER** | `menu:catalog:view`, `orders:view`, `billing:invoice:create`, `billing:payment:process`, `orders:status:update` |
| **KITCHEN_STAFF** | `menu:catalog:view`, `menu:item:toggle_stock`, `kds:display:view`, `kds:ticket:bump`, `orders:view` |
| **WAITER** | `menu:catalog:view`, `orders:view`, `orders:status:update`, `waiter:dispatch:view`, `waiter:dispatch:serve` |
| **CUSTOMER_GUEST** | `menu:catalog:view`, `orders:create`, `orders:view` (restricted to own session) |