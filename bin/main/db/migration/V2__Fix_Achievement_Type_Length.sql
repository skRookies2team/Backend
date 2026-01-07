-- Fix Achievement type column length for existing databases
-- This migration ensures the 'type' column in achievements table has sufficient length
-- to store all enum values (e.g., "COMPLETION_COUNT")

ALTER TABLE achievements MODIFY COLUMN type VARCHAR(50) NOT NULL;
