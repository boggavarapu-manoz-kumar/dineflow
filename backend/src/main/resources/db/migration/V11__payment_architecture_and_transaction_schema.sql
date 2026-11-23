-- ==============================================================================
-- Flyway Database Migration: V11__payment_architecture_and_transaction_schema.sql
-- Description: Core schema for payment records, payment attempts/transactions,
--              provider abstraction metadata, webhook events, and payment audit logs.
-- ==============================================================================

-- 1. Payments Table (High-level business payment record linked to a bill)
CREATE TABLE IF NOT EXISTS restaurant_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    bill_id UUID NOT NULL REFERENCES restaurant_bills(id) ON DELETE RESTRICT,
    payment_reference VARCHAR(64) NOT NULL,
    payment_method VARCHAR(32) NOT NULL, -- CASH, PAY_AT_COUNTER, ONLINE_PAYMENT
    payment_status VARCHAR(32) NOT NULL DEFAULT 'CREATED', -- CREATED, PENDING, INITIATED, PROCESSING, SUCCEEDED, FAILED, CANCELLED, PARTIALLY_REFUNDED, REFUNDED
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    payable_amount NUMERIC(10, 2) NOT NULL,
    paid_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    refunded_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    payment_provider VARCHAR(32) NOT NULL DEFAULT 'INTERNAL', -- INTERNAL, CASH_REGISTER, RAZORPAY, STRIPE, MOCK
    provider_payment_id VARCHAR(128),
    notes VARCHAR(255),
    collected_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_payment_ref UNIQUE (tenant_id, payment_reference)
);

CREATE INDEX IF NOT EXISTS idx_payments_bill ON restaurant_payments(bill_id);
CREATE INDEX IF NOT EXISTS idx_payments_tenant_branch_status ON restaurant_payments(tenant_id, branch_id, payment_status);
CREATE INDEX IF NOT EXISTS idx_payments_provider_id ON restaurant_payments(tenant_id, provider_payment_id);

-- 2. Payment Attempts / Transactions (Tracks low-level provider sessions, intents, retries)
CREATE TABLE IF NOT EXISTS payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES restaurant_payments(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(128) NOT NULL,
    provider_name VARCHAR(32) NOT NULL,
    provider_order_id VARCHAR(128),
    provider_transaction_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'INITIATED', -- INITIATED, PROCESSING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED
    amount NUMERIC(10, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    failure_code VARCHAR(64),
    failure_message VARCHAR(512),
    client_redirect_url VARCHAR(1024),
    raw_provider_response TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_attempt_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_payment ON payment_attempts(payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_order ON payment_attempts(tenant_id, provider_order_id);

-- 3. Payment Audit Events (Immutable append-only ledger for all monetary status transitions)
CREATE TABLE IF NOT EXISTS payment_audit_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES restaurant_payments(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL, -- CREATED, ATTEMPT_INITIATED, SETTLED_OFFLINE, WEBHOOK_VERIFIED, PROVIDER_DECLINED, REFUNDED
    actor_id UUID,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM', -- STAFF, CUSTOMER, WEBHOOK, SYSTEM
    amount_delta NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    audit_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_audit_payment ON payment_audit_records(payment_id);

-- 4. Register Payment Permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000060', 'PAYMENT_VIEW', 'View payment records and attempt history', 'PAYMENT'),
    ('10000000-0000-0000-0000-000000000061', 'PAYMENT_PROCESS', 'Record and verify in-person or gateway payments', 'PAYMENT'),
    ('10000000-0000-0000-0000-000000000062', 'PAYMENT_CANCEL', 'Cancel pending payment attempts', 'PAYMENT')
ON CONFLICT (code) DO NOTHING;

-- Map permissions to roles
-- HOTEL_OWNER, HOTEL_ADMIN, BRANCH_MANAGER get all payment permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER')
  AND p.code IN ('PAYMENT_VIEW', 'PAYMENT_PROCESS', 'PAYMENT_CANCEL')
ON CONFLICT DO NOTHING;

-- WAITER gets PAYMENT_VIEW and PAYMENT_PROCESS
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'WAITER'
  AND p.code IN ('PAYMENT_VIEW', 'PAYMENT_PROCESS')
ON CONFLICT DO NOTHING;
