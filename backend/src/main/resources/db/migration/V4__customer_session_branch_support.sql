-- ==============================================================================
-- Flyway Database Migration: V4__customer_session_branch_support.sql
-- Description: Add branch_id foreign key to customer_sessions to support
--              branch-aware dining sessions, QR table binding, and branch pricing.
-- ==============================================================================

ALTER TABLE customer_sessions
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_customer_sessions_branch_id ON customer_sessions(branch_id);
