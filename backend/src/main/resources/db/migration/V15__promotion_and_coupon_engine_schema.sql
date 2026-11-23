-- ==============================================================================
-- Flyway Database Migration: V15__promotion_and_coupon_engine_schema.sql
-- Description: Multi-tenant promotion engine, coupon management, eligible targets,
--              concurrency-safe redemption ledger, and RBAC permissions.
-- ==============================================================================

-- 1. Promotions Table
CREATE TABLE IF NOT EXISTS promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    coupon_code VARCHAR(64),
    discount_type VARCHAR(32) NOT NULL, -- FIXED_AMOUNT, PERCENTAGE
    discount_value NUMERIC(10, 2) NOT NULL,
    scope VARCHAR(32) NOT NULL DEFAULT 'ORDER_LEVEL', -- ORDER_LEVEL, ITEM_SPECIFIC, CATEGORY_SPECIFIC
    min_order_value NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    max_discount_amount NUMERIC(10, 2),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    usage_limit INT,
    current_usage_count INT NOT NULL DEFAULT 0,
    per_customer_limit INT,
    apply_before_tax BOOLEAN NOT NULL DEFAULT TRUE,
    combinable BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_coupon_code UNIQUE (tenant_id, coupon_code),
    CONSTRAINT chk_promotion_discount_positive CHECK (discount_value > 0),
    CONSTRAINT chk_promotion_usage_non_negative CHECK (current_usage_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_promotions_tenant_active ON promotions(tenant_id, active);
CREATE INDEX IF NOT EXISTS idx_promotions_tenant_coupon ON promotions(tenant_id, coupon_code);
CREATE INDEX IF NOT EXISTS idx_promotions_tenant_branch ON promotions(tenant_id, branch_id);

-- 2. Eligible Menu Items for ITEM_SPECIFIC promotions
CREATE TABLE IF NOT EXISTS promotion_eligible_items (
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL,
    PRIMARY KEY (promotion_id, menu_item_id)
);

-- 3. Eligible Categories for CATEGORY_SPECIFIC promotions
CREATE TABLE IF NOT EXISTS promotion_eligible_categories (
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    PRIMARY KEY (promotion_id, category_id)
);

-- 4. Promotion Redemptions Ledger
CREATE TABLE IF NOT EXISTS promotion_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE RESTRICT,
    bill_id UUID NOT NULL REFERENCES restaurant_bills(id) ON DELETE RESTRICT,
    customer_session_id UUID,
    coupon_code_snapshot VARCHAR(64),
    promotion_name_snapshot VARCHAR(128) NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL DEFAULT 'APPLIED', -- APPLIED, REMOVED
    applied_by_user_id UUID,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER', -- CUSTOMER, STAFF
    redeemed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_redemptions_promotion ON promotion_redemptions(promotion_id, status);
CREATE INDEX IF NOT EXISTS idx_redemptions_bill ON promotion_redemptions(bill_id, status);
CREATE INDEX IF NOT EXISTS idx_redemptions_customer ON promotion_redemptions(tenant_id, promotion_id, customer_session_id, status);

-- 5. Register Promotion Permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000070', 'PROMOTION_VIEW', 'View promotions, coupons, and redemption analytics', 'PROMOTION'),
    ('10000000-0000-0000-0000-000000000071', 'PROMOTION_MANAGE', 'Create, edit, activate, and deactivate promotions', 'PROMOTION'),
    ('10000000-0000-0000-0000-000000000072', 'PROMOTION_APPLY', 'Apply or remove promotions and coupons on bills', 'PROMOTION')
ON CONFLICT (code) DO NOTHING;

-- Map permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER')
  AND p.code IN ('PROMOTION_VIEW', 'PROMOTION_MANAGE', 'PROMOTION_APPLY')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'WAITER'
  AND p.code IN ('PROMOTION_VIEW', 'PROMOTION_APPLY')
ON CONFLICT DO NOTHING;
