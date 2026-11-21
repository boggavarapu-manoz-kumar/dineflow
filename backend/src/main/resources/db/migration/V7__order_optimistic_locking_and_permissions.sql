-- ==============================================================================
-- Flyway Database Migration: V7__order_optimistic_locking_and_permissions.sql
-- Description: Add version column to restaurant_orders for optimistic concurrency
--              control and register order management permissions.
-- ==============================================================================

-- 1. Add version column to restaurant_orders for concurrency protection
ALTER TABLE restaurant_orders
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. Add branch_id, tenant_id, and actor_type columns to restaurant_order_status_history
ALTER TABLE restaurant_order_status_history
    ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(32) NOT NULL DEFAULT 'STAFF';

-- 3. Register additional permissions if not already present
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000030', 'ORDER_ACCEPT', 'Accept placed dining orders', 'ORDER'),
    ('10000000-0000-0000-0000-000000000031', 'ORDER_SERVE', 'Mark prepared orders as served to guest table', 'ORDER'),
    ('10000000-0000-0000-0000-000000000032', 'ORDER_CANCEL', 'Cancel active dining orders with mandatory reason', 'ORDER')
ON CONFLICT (code) DO NOTHING;

-- 4. Associate permissions with roles
-- HOTEL_OWNER, HOTEL_ADMIN, BRANCH_MANAGER get ORDER_ACCEPT, ORDER_SERVE, ORDER_CANCEL
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER')
  AND p.code IN ('ORDER_ACCEPT', 'ORDER_SERVE', 'ORDER_CANCEL')
ON CONFLICT DO NOTHING;

-- WAITER gets ORDER_ACCEPT and ORDER_SERVE
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'WAITER'
  AND p.code IN ('ORDER_ACCEPT', 'ORDER_SERVE')
ON CONFLICT DO NOTHING;
