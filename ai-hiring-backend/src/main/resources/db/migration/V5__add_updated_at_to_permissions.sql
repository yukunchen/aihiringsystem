-- V5__add_updated_at_to_permissions.sql

-- Add updated_at column to permissions table (missing from V1)
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add updated_at column to refresh_tokens table (missing from V1)
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
