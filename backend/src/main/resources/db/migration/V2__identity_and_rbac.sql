-- ==============================================================================
-- Flyway Database Migration: V2__identity_and_rbac.sql
-- Description: Enterprise multi-tenant identity, credentials, roles, permissions,
--              memberships, rotating refresh sessions, guest sessions, and audit logs.
-- ==============================================================================

-- 1. Users Table (Global user identities)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(128) NOT NULL,
    phone_number VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INVITED, DISABLED, LOCKED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- 2. Staff Credentials Table (Strictly isolated from public user profile)
CREATE TABLE IF NOT EXISTS staff_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    password_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_staff_credentials_user ON staff_credentials(user_id);

-- 3. Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Permissions Table
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255),
    category VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Role-Permissions Join Table
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 6. User Memberships Table (Tenant-specific roles & platform roles)
CREATE TABLE IF NOT EXISTS user_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE, -- NULL for platform global roles
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_tenant_membership UNIQUE (user_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_user_memberships_tenant ON user_memberships(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_memberships_user ON user_memberships(user_id);

-- 7. Refresh Sessions Table (Hashed tokens, family revocation)
CREATE TABLE IF NOT EXISTS refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_agent VARCHAR(512),
    ip_address VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_sessions_hash ON refresh_sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_family ON refresh_sessions(family_id);

-- 8. Customer Guest Sessions Table
CREATE TABLE IF NOT EXISTS customer_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    table_number VARCHAR(32) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    masked_mobile VARCHAR(32) NOT NULL,
    session_token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CLOSED, EXPIRED
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_sessions_lookup ON customer_sessions(tenant_id, table_number, status);

-- 9. Password Reset Tokens Table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_hash ON password_reset_tokens(token_hash);

-- 10. Login Audit Logs Table
CREATE TABLE IF NOT EXISTS login_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    tenant_id UUID,
    outcome VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255),
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_audit_email ON login_audit_logs(email);
CREATE INDEX IF NOT EXISTS idx_login_audit_created ON login_audit_logs(created_at);

-- -----------------------------------------------------------------------------
-- Seed Roles & Granular Permissions
-- -----------------------------------------------------------------------------
INSERT INTO roles (id, code, name, description, is_system_role) VALUES
    ('00000000-0000-0000-0000-000000000001', 'SUPER_ADMIN', 'Super Administrator', 'SaaS Platform Super Administrator', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'PLATFORM_SUPPORT', 'Platform Support', 'SaaS Platform Support Personnel', TRUE),
    ('00000000-0000-0000-0000-000000000003', 'HOTEL_OWNER', 'Hotel Owner', 'Hotel Business Owner with complete management control', TRUE),
    ('00000000-0000-0000-0000-000000000004', 'HOTEL_ADMIN', 'Hotel Administrator', 'Hotel Operational Administrator', TRUE),
    ('00000000-0000-0000-0000-000000000005', 'BRANCH_MANAGER', 'Branch Manager', 'Manager supervising branch shifts and staff', TRUE),
    ('00000000-0000-0000-0000-000000000006', 'CASHIER', 'Cashier', 'Billing, invoicing and cashier desk operator', TRUE),
    ('00000000-0000-0000-0000-000000000007', 'WAITER', 'Waiter', 'Waitstaff taking orders and delivering dishes', TRUE),
    ('00000000-0000-0000-0000-000000000008', 'KITCHEN_STAFF', 'Kitchen Staff', 'Kitchen Chef and line cooks updating preparation states', TRUE),
    ('00000000-0000-0000-0000-000000000009', 'CUSTOMER_GUEST', 'Customer Guest', 'Anonymous dining guest placing table orders', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (id, code, description, category) VALUES
    -- Platform permissions
    ('10000000-0000-0000-0000-000000000001', 'PLATFORM_MANAGE', 'Super Admin platform oversight', 'PLATFORM'),
    ('10000000-0000-0000-0000-000000000002', 'TENANT_VIEW', 'View all hotel tenants', 'PLATFORM'),
    ('10000000-0000-0000-0000-000000000003', 'TENANT_MANAGE', 'Onboard, suspend, or configure tenants', 'PLATFORM'),
    ('10000000-0000-0000-0000-000000000004', 'PLATFORM_ANALYTICS_VIEW', 'View platform-wide revenue and performance metrics', 'PLATFORM'),

    -- Hotel Administration & Governance
    ('10000000-0000-0000-0000-000000000005', 'HOTEL_VIEW', 'View hotel profile and settings', 'HOTEL'),
    ('10000000-0000-0000-0000-000000000006', 'HOTEL_MANAGE', 'Manage hotel profile, branding, taxes', 'HOTEL'),
    ('10000000-0000-0000-0000-000000000007', 'SUBSCRIPTION_MANAGE', 'Manage SaaS billing and subscriptions', 'HOTEL'),
    ('10000000-0000-0000-0000-000000000008', 'ANALYTICS_VIEW', 'View hotel financial and sales reports', 'HOTEL'),

    -- Staff Management
    ('10000000-0000-0000-0000-000000000009', 'STAFF_VIEW', 'View hotel staff list and profiles', 'STAFF'),
    ('10000000-0000-0000-0000-000000000010', 'STAFF_CREATE', 'Create and invite hotel staff members', 'STAFF'),
    ('10000000-0000-0000-0000-000000000011', 'STAFF_UPDATE', 'Update and revoke staff accounts and roles', 'STAFF'),

    -- Menu & Catalog
    ('10000000-0000-0000-0000-000000000012', 'MENU_VIEW', 'Browse menu items and categories', 'MENU'),
    ('10000000-0000-0000-0000-000000000013', 'MENU_MANAGE', 'Create and modify menu items, prices, modifiers', 'MENU'),

    -- Table & Branch
    ('10000000-0000-0000-0000-000000000014', 'BRANCH_VIEW', 'View branch operations', 'BRANCH'),
    ('10000000-0000-0000-0000-000000000015', 'WAITER_ASSIGN', 'Assign waitstaff to tables or sections', 'BRANCH'),

    -- Orders
    ('10000000-0000-0000-0000-000000000016', 'ORDER_VIEW', 'View orders across tables', 'ORDER'),
    ('10000000-0000-0000-0000-000000000017', 'ORDER_MANAGE', 'Override, discount, or cancel orders', 'ORDER'),

    -- Cashier & Billing
    ('10000000-0000-0000-0000-000000000018', 'BILL_VIEW', 'View bills and customer invoices', 'BILLING'),
    ('10000000-0000-0000-0000-000000000019', 'BILL_MANAGE', 'Generate invoices, apply discounts, close bills', 'BILLING'),
    ('10000000-0000-0000-0000-000000000020', 'PAYMENT_VIEW', 'View payment transaction logs', 'BILLING'),

    -- Waiter
    ('10000000-0000-0000-0000-000000000021', 'WAITER_VIEW', 'View table dispatch queue', 'WAITER'),
    ('10000000-0000-0000-0000-000000000022', 'SERVICE_REQUEST_VIEW', 'View guest table calls and alerts', 'WAITER'),
    ('10000000-0000-0000-0000-000000000023', 'SERVICE_REQUEST_UPDATE', 'Acknowledge and clear guest calls', 'WAITER'),

    -- Kitchen (KDS)
    ('10000000-0000-0000-0000-000000000024', 'KITCHEN_VIEW', 'View real-time KDS preparation display', 'KITCHEN'),
    ('10000000-0000-0000-0000-000000000025', 'KITCHEN_UPDATE', 'Update preparation and ready states in KDS', 'KITCHEN'),

    -- Customer Guest
    ('10000000-0000-0000-0000-000000000026', 'CUSTOMER_MENU_VIEW', 'View active digital menu for dining table', 'CUSTOMER'),
    ('10000000-0000-0000-0000-000000000027', 'CUSTOMER_ORDER_CREATE', 'Submit customer table order', 'CUSTOMER'),
    ('10000000-0000-0000-0000-000000000028', 'CUSTOMER_ORDER_VIEW_OWN', 'View own active table order items', 'CUSTOMER'),
    ('10000000-0000-0000-0000-000000000029', 'CUSTOMER_SERVICE_REQUEST_CREATE', 'Call waiter or request water/assistance', 'CUSTOMER')
ON CONFLICT (code) DO NOTHING;

-- Map permissions according to the documented role matrix
-- 1. SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions WHERE code IN (
    'PLATFORM_MANAGE', 'TENANT_VIEW', 'TENANT_MANAGE', 'PLATFORM_ANALYTICS_VIEW'
) ON CONFLICT DO NOTHING;

-- 2. HOTEL_OWNER
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id FROM permissions WHERE code IN (
    'HOTEL_VIEW', 'HOTEL_MANAGE', 'STAFF_VIEW', 'STAFF_CREATE', 'STAFF_UPDATE',
    'MENU_MANAGE', 'MENU_VIEW', 'ORDER_VIEW', 'ORDER_MANAGE', 'ANALYTICS_VIEW', 'SUBSCRIPTION_MANAGE',
    'BILL_VIEW', 'BILL_MANAGE', 'PAYMENT_VIEW', 'KITCHEN_VIEW', 'WAITER_VIEW'
) ON CONFLICT DO NOTHING;

-- 3. HOTEL_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000004', id FROM permissions WHERE code IN (
    'HOTEL_VIEW', 'STAFF_VIEW', 'STAFF_CREATE', 'STAFF_UPDATE',
    'MENU_MANAGE', 'MENU_VIEW', 'ORDER_VIEW', 'ORDER_MANAGE', 'ANALYTICS_VIEW',
    'BILL_VIEW', 'BILL_MANAGE', 'PAYMENT_VIEW', 'KITCHEN_VIEW', 'WAITER_VIEW'
) ON CONFLICT DO NOTHING;

-- 4. BRANCH_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000005', id FROM permissions WHERE code IN (
    'BRANCH_VIEW', 'STAFF_VIEW', 'ORDER_VIEW', 'ORDER_MANAGE', 'MENU_VIEW',
    'KITCHEN_VIEW', 'WAITER_ASSIGN'
) ON CONFLICT DO NOTHING;

-- 5. CASHIER
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000006', id FROM permissions WHERE code IN (
    'BILL_VIEW', 'BILL_MANAGE', 'PAYMENT_VIEW', 'ORDER_VIEW'
) ON CONFLICT DO NOTHING;

-- 6. WAITER
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000007', id FROM permissions WHERE code IN (
    'WAITER_VIEW', 'ORDER_VIEW', 'SERVICE_REQUEST_VIEW', 'SERVICE_REQUEST_UPDATE', 'MENU_VIEW'
) ON CONFLICT DO NOTHING;

-- 7. KITCHEN_STAFF
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000008', id FROM permissions WHERE code IN (
    'KITCHEN_VIEW', 'KITCHEN_UPDATE', 'MENU_VIEW'
) ON CONFLICT DO NOTHING;

-- 8. CUSTOMER_GUEST
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000009', id FROM permissions WHERE code IN (
    'CUSTOMER_MENU_VIEW', 'CUSTOMER_ORDER_CREATE', 'CUSTOMER_ORDER_VIEW_OWN', 'CUSTOMER_SERVICE_REQUEST_CREATE'
) ON CONFLICT DO NOTHING;