-- V2: Extend deposit table with status and confirmation fields

-- Add new columns to deposit table
ALTER TABLE deposit
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN confirmed_at TIMESTAMPTZ NULL;

-- Create unique partial index on tx_hash (only for non-null values)
CREATE UNIQUE INDEX idx_deposit_tx_hash_unique 
    ON deposit(tx_hash) 
    WHERE tx_hash IS NOT NULL;

-- Update existing deposits to CONFIRMED status (they were already credited)
UPDATE deposit SET status = 'CONFIRMED', confirmed_at = created_at WHERE status = 'PENDING';

