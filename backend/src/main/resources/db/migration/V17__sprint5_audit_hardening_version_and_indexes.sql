-- ==============================================================================
-- Flyway Database Migration: V17__sprint5_audit_hardening_version_and_indexes.sql
-- Description: Adds optimistic locking version column to restaurant_bills and
--              indexed lookup columns for webhook attempt resolution.
-- ==============================================================================

ALTER TABLE restaurant_bills
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_order_lookup
    ON payment_attempts(provider_order_id);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_txn_lookup
    ON payment_attempts(provider_transaction_id);
