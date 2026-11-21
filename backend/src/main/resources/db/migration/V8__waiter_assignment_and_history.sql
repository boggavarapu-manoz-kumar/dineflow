-- ==============================================================================
-- Flyway Database Migration: V8__waiter_assignment_and_history.sql
-- Description: Add assigned_waiter_id to restaurant_orders, create
--              restaurant_order_assignments history table, and assign permissions.
-- ==============================================================================

-- 1. Add assigned_waiter_id to restaurant_orders for immediate query resolution
ALTER TABLE restaurant_orders
    ADD COLUMN IF NOT EXISTS assigned_waiter_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_orders_assigned_waiter ON restaurant_orders(tenant_id, assigned_waiter_id);

-- 2. Restaurant Order Assignments History Table
CREATE TABLE IF NOT EXISTS restaurant_order_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES restaurant_orders(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    waiter_id UUID REFERENCES users(id) ON DELETE SET NULL,
    assigned_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assignment_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REASSIGNED, UNASSIGNED
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_order_assignments_order ON restaurant_order_assignments(order_id);
CREATE INDEX IF NOT EXISTS idx_order_assignments_waiter ON restaurant_order_assignments(tenant_id, waiter_id, assignment_status);

-- 3. Ensure WAITER_ASSIGN permission is mapped to HOTEL_OWNER, HOTEL_ADMIN, and BRANCH_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER')
  AND p.code = 'WAITER_ASSIGN'
ON CONFLICT DO NOTHING;
