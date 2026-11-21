-- ==============================================================================
-- Flyway Database Migration: V6__restaurant_orders_and_snapshots.sql
-- Description: Core schema for restaurant orders, immutable line items, option snapshots,
--              status transition audit history, and customer-bound idempotency keys.
-- ==============================================================================

-- 1. Restaurant Orders Table
CREATE TABLE IF NOT EXISTS restaurant_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    customer_session_id UUID NOT NULL REFERENCES customer_sessions(id) ON DELETE CASCADE,
    table_number VARCHAR(32) NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PLACED',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal NUMERIC(10, 2) NOT NULL,
    tax_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    service_charge_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    service_charge_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    grand_total NUMERIC(10, 2) NOT NULL,
    total_item_count INT NOT NULL,
    special_notes VARCHAR(512),
    cart_id UUID REFERENCES customer_carts(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_order_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX IF NOT EXISTS idx_orders_tenant_branch ON restaurant_orders(tenant_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer_session ON restaurant_orders(customer_session_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON restaurant_orders(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON restaurant_orders(tenant_id, created_at DESC);

-- 2. Restaurant Order Items Table (Immutable historical snapshot)
CREATE TABLE IF NOT EXISTS restaurant_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES restaurant_orders(id) ON DELETE CASCADE,
    menu_item_id UUID REFERENCES menu_items(id) ON DELETE SET NULL,
    variant_id UUID REFERENCES menu_variants(id) ON DELETE SET NULL,
    item_name_snapshot VARCHAR(128) NOT NULL,
    variant_name_snapshot VARCHAR(64),
    item_description_snapshot VARCHAR(1024),
    quantity INT NOT NULL CHECK (quantity > 0 AND quantity <= 50),
    base_unit_price NUMERIC(10, 2) NOT NULL,
    options_unit_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    effective_unit_price NUMERIC(10, 2) NOT NULL,
    line_total NUMERIC(10, 2) NOT NULL,
    special_instructions VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON restaurant_order_items(order_id);

-- 3. Restaurant Order Item Options Table (Immutable modifier snapshots)
CREATE TABLE IF NOT EXISTS restaurant_order_item_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id UUID NOT NULL REFERENCES restaurant_order_items(id) ON DELETE CASCADE,
    option_id UUID REFERENCES menu_options(id) ON DELETE SET NULL,
    option_name_snapshot VARCHAR(128) NOT NULL,
    price_contribution NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_item_options_item ON restaurant_order_item_options(order_item_id);

-- 4. Restaurant Order Status History Table (Audit Trail)
CREATE TABLE IF NOT EXISTS restaurant_order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES restaurant_orders(id) ON DELETE CASCADE,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    changed_by_user_id UUID,
    changed_by_role VARCHAR(64) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order ON restaurant_order_status_history(order_id);

-- 5. Order Idempotency Records Table
CREATE TABLE IF NOT EXISTS order_idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_session_id UUID NOT NULL REFERENCES customer_sessions(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID REFERENCES restaurant_orders(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    response_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_session_idempotency_key UNIQUE (customer_session_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_records_lookup ON order_idempotency_records(customer_session_id, idempotency_key);
