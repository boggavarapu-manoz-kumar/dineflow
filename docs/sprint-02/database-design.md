# DineFlow Sprint 2: Database Schema & Entity Relationships

## 1. Migration Specification: `V2__identity_and_rbac.sql`

```sql
-- -----------------------------------------------------------------------------
-- 1. Users: Central principal records (staff & super admin)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(64) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    phone_number VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, PENDING_INVITE
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- -----------------------------------------------------------------------------
-- 2. Roles & Permissions (Granular RBAC)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255),
    category VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- -----------------------------------------------------------------------------
-- 3. User Memberships: Multi-Tenant Binding
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE, -- NULL for platform global roles
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_tenant_membership UNIQUE (user_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_user_memberships_tenant ON user_memberships(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_memberships_user ON user_memberships(user_id);

-- -----------------------------------------------------------------------------
-- 4. Refresh Sessions (Token Rotation & Revocation)
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- 5. Customer Guest Sessions: Ephemeral Table Orders
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS guest_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    table_number VARCHAR(32) NOT NULL,
    guest_name VARCHAR(128) NOT NULL,
    mobile_number VARCHAR(32) NOT NULL,
    session_token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CLOSED, EXPIRED
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_guest_sessions_lookup ON guest_sessions(tenant_id, table_number, status);

-- -----------------------------------------------------------------------------
-- 6. Login Audit Logs: Security Telemetry
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS login_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    tenant_id UUID,
    outcome VARCHAR(32) NOT NULL, -- SUCCESS, FAILED_CREDENTIALS, LOCKED, REUSE_DETECTED
    failure_reason VARCHAR(255),
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_audit_email ON login_audit_logs(email);
CREATE INDEX IF NOT EXISTS idx_login_audit_created ON login_audit_logs(created_at);
```