-- Add from_address column to deposit table for source address verification
ALTER TABLE deposit ADD COLUMN from_address VARCHAR(128);

-- Create index for faster lookups
CREATE INDEX idx_deposit_from_address ON deposit(from_address);

