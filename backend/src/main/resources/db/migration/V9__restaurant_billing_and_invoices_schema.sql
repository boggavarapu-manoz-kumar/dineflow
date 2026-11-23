-- ==============================================================================
-- Flyway Database Migration: V9__restaurant_billing_and_invoices_schema.sql
-- Description: Core schema for restaurant bills, bill item snapshots, order mappings,
--              status transitions, discounts, and sequential fiscal bill numbers.
-- ==============================================================================

-- 1. Restaurant Bills Table
CREATE TABLE IF NOT EXISTS restaurant_bills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    customer_session_id UUID REFERENCES customer_sessions(id) ON DELETE SET NULL,
    bill_number VARCHAR(64) NOT NULL,
    table_number VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ISSUED', -- DRAFT, ISSUED, PARTIALLY_PAID, PAID, VOIDED
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    restaurant_name_snapshot VARCHAR(128) NOT NULL,
    branch_name_snapshot VARCHAR(128),
    branch_address_snapshot VARCHAR(255),
    subtotal NUMERIC(10, 2) NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    tax_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    service_charge_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    service_charge_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    grand_total NUMERIC(10, 2) NOT NULL,
    paid_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    outstanding_balance NUMERIC(10, 2) NOT NULL,
    void_reason VARCHAR(255),
    voided_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    issued_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP WITH TIME ZONE,
    voided_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_bill_number UNIQUE (tenant_id, bill_number)
);

CREATE INDEX IF NOT EXISTS idx_bills_tenant_branch_status ON restaurant_bills(tenant_id, branch_id, status);
CREATE INDEX IF NOT EXISTS idx_bills_session ON restaurant_bills(customer_session_id);
CREATE INDEX IF NOT EXISTS idx_bills_table ON restaurant_bills(tenant_id, branch_id, table_number);
CREATE INDEX IF NOT EXISTS idx_bills_created_at ON restaurant_bills(tenant_id, created_at DESC);

-- 2. Bill to Order Mappings Table (Relates one or more orders to a bill)
CREATE TABLE IF NOT EXISTS bill_order_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id UUID NOT NULL REFERENCES restaurant_bills(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES restaurant_orders(id) ON DELETE CASCADE,
    order_number VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bill_order UNIQUE (bill_id, order_id)
);

CREATE INDEX IF NOT EXISTS idx_bill_orders_bill ON bill_order_mappings(bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_orders_order ON bill_order_mappings(order_id);

-- 3. Bill Line Items Table (Frozen snapshot of billed items)
CREATE TABLE IF NOT EXISTS restaurant_bill_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id UUID NOT NULL REFERENCES restaurant_bills(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES restaurant_orders(id) ON DELETE CASCADE,
    order_item_id UUID REFERENCES restaurant_order_items(id) ON DELETE SET NULL,
    item_name_snapshot VARCHAR(128) NOT NULL,
    variant_name_snapshot VARCHAR(64),
    item_description_snapshot VARCHAR(1024),
    quantity INT NOT NULL CHECK (quantity > 0),
    base_unit_price NUMERIC(10, 2) NOT NULL,
    options_unit_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    effective_unit_price NUMERIC(10, 2) NOT NULL,
    line_total NUMERIC(10, 2) NOT NULL,
    special_instructions VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bill_items_bill ON restaurant_bill_items(bill_id);

-- 4. Bill Item Options Table (Frozen modifier snapshots)
CREATE TABLE IF NOT EXISTS restaurant_bill_item_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_item_id UUID NOT NULL REFERENCES restaurant_bill_items(id) ON DELETE CASCADE,
    option_id UUID REFERENCES menu_options(id) ON DELETE SET NULL,
    option_name_snapshot VARCHAR(128) NOT NULL,
    price_contribution NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bill_item_options_item ON restaurant_bill_item_options(bill_item_id);

-- 5. Bill Status Audit History
CREATE TABLE IF NOT EXISTS restaurant_bill_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id UUID NOT NULL REFERENCES restaurant_bills(id) ON DELETE CASCADE,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    changed_by_user_id UUID,
    changed_by_role VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'STAFF', -- STAFF, CUSTOMER, SYSTEM
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bill_history_bill ON restaurant_bill_status_history(bill_id);

-- 6. Register Billing Permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000040', 'BILL_VIEW', 'View restaurant bills and item breakdowns', 'BILLING'),
    ('10000000-0000-0000-0000-000000000041', 'BILL_CREATE', 'Generate bills from dining orders', 'BILLING'),
    ('10000000-0000-0000-0000-000000000042', 'BILL_VOID', 'Void unpaid restaurant bills with reason', 'BILLING'),
    ('10000000-0000-0000-0000-000000000043', 'BILL_COLLECT_PAYMENT', 'Collect payment against an active bill', 'BILLING')
ON CONFLICT (code) DO NOTHING;

-- Map permissions to roles
-- HOTEL_OWNER, HOTEL_ADMIN, BRANCH_MANAGER get all billing permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER')
  AND p.code IN ('BILL_VIEW', 'BILL_CREATE', 'BILL_VOID', 'BILL_COLLECT_PAYMENT')
ON CONFLICT DO NOTHING;

-- WAITER gets BILL_VIEW, BILL_CREATE, BILL_COLLECT_PAYMENT
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'WAITER'
  AND p.code IN ('BILL_VIEW', 'BILL_CREATE', 'BILL_COLLECT_PAYMENT')
ON CONFLICT DO NOTHING;
