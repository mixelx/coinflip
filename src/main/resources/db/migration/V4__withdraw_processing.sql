-- Add processing fields to withdraw_request table

-- Add new columns (if not exist)
ALTER TABLE withdraw_request
ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;

ALTER TABLE withdraw_request
ADD COLUMN IF NOT EXISTS last_error TEXT NULL;

ALTER TABLE withdraw_request
ADD COLUMN IF NOT EXISTS tx_hash VARCHAR(128) NULL;

ALTER TABLE withdraw_request
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE withdraw_request
ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ NULL;

-- Index for worker to find CREATED requests efficiently
CREATE INDEX IF NOT EXISTS idx_withdraw_status_created ON withdraw_request(status, created_at);

-- Unique constraint on tx_hash to prevent double processing
CREATE UNIQUE INDEX IF NOT EXISTS ux_withdraw_tx_hash ON withdraw_request(tx_hash) WHERE tx_hash IS NOT NULL;

-- Index for recovery of stuck PROCESSING requests
CREATE INDEX IF NOT EXISTS idx_withdraw_processing_updated ON withdraw_request(status, updated_at) WHERE status = 'PROCESSING';


