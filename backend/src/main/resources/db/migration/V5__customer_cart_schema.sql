-- ==============================================================================
-- Flyway Database Migration: V5__customer_cart_schema.sql
-- Description: Core schema for customer carts, cart items, selected options,
--              and optimistic locking versioning for high-concurrency cart operations.
-- ==============================================================================

-- 1. Customer Carts Table
CREATE TABLE IF NOT EXISTS customer_carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    customer_session_id UUID NOT NULL REFERENCES customer_sessions(id) ON DELETE CASCADE,
    table_number VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customer_carts_session UNIQUE (customer_session_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_carts_tenant_branch ON customer_carts(tenant_id, branch_id);

-- 2. Customer Cart Items Table
CREATE TABLE IF NOT EXISTS customer_cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES customer_carts(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    variant_id UUID REFERENCES menu_variants(id) ON DELETE CASCADE,
    quantity INT NOT NULL CHECK (quantity > 0 AND quantity <= 50),
    special_instructions VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON customer_cart_items(cart_id);

-- 3. Cart Item Options (Selected modifiers/add-ons) Table
CREATE TABLE IF NOT EXISTS customer_cart_item_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_item_id UUID NOT NULL REFERENCES customer_cart_items(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES menu_options(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cart_item_option UNIQUE (cart_item_id, option_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_item_options_item ON customer_cart_item_options(cart_item_id);
