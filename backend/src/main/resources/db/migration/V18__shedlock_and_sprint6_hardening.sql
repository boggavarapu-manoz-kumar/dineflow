-- ==============================================================================
-- Flyway Database Migration: V18__shedlock_and_sprint6_hardening.sql
-- Description: Creates distributed ShedLock table for multi-replica scheduled
--              payment reconciliation and operational safety.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
