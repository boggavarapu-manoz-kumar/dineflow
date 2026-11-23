-- ==============================================================================
-- Flyway Database Migration: V14__refund_domain_and_audit_schema.sql
-- Description: Core schema for discrete refund management, approval workflow,
--              provider tracking, and financial audit preservation.
-- ==============================================================================

-- 1. Create Refunds Table
CREATE TABLE IF NOT EXISTS restaurant_refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    payment_id UUID NOT NULL REFERENCES restaurant_payments(id) ON DELETE RESTRICT,
    bill_id UUID NOT NULL REFERENCES restaurant_bills(id) ON DELETE RESTRICT,
    refund_reference VARCHAR(64) NOT NULL,
    refund_amount NUMERIC(10, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    refund_method VARCHAR(32) NOT NULL, -- CASH, ONLINE_GATEWAY, ADJUSTMENT
    refund_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL', -- PENDING_APPROVAL, APPROVED, INITIATED, SUCCEEDED, FAILED, REJECTED
    reason VARCHAR(512) NOT NULL,
    provider_refund_id VARCHAR(128),
    failure_message VARCHAR(512),
    requested_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_refund_ref UNIQUE (tenant_id, refund_reference)
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment ON restaurant_refunds(payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_bill ON restaurant_refunds(bill_id);
CREATE INDEX IF NOT EXISTS idx_refunds_tenant_branch_status ON restaurant_refunds(tenant_id, branch_id, refund_status);

-- 2. Add REFUND_APPROVE and REFUND_EXECUTE permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000063', 'REFUND_REQUEST', 'Request full or partial customer refund', 'PAYMENT'),
    ('10000000-0000-0000-0000-000000000064', 'REFUND_APPROVE', 'Authorize and approve customer refund requests', 'PAYMENT'),
    ('10000000-0000-0000-0000-000000000065', 'REFUND_EXECUTE', 'Execute online gateway or physical cash refunds', 'PAYMENT')
ON CONFLICT (code) DO NOTHING;

-- Map permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN')
  AND p.code IN ('REFUND_REQUEST', 'REFUND_APPROVE', 'REFUND_EXECUTE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'BRANCH_MANAGER'
  AND p.code IN ('REFUND_REQUEST', 'REFUND_APPROVE', 'REFUND_EXECUTE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'WAITER'
  AND p.code = 'REFUND_REQUEST'
ON CONFLICT DO NOTHING;
