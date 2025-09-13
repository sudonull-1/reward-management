-- Migration to add source reward tracking for FIFO coin redemption/expiry
-- This allows tracking which specific reward transactions coins are redeemed/expired from

-- Add source_reward_id column to transactions table
ALTER TABLE transactions 
ADD COLUMN source_reward_id UUID;

-- Add foreign key constraint
ALTER TABLE transactions 
ADD CONSTRAINT fk_transaction_source_reward 
FOREIGN KEY (source_reward_id) REFERENCES transactions(transaction_id);

-- Add index for better query performance
CREATE INDEX idx_transactions_source_reward ON transactions(source_reward_id);

-- Add comments for documentation
COMMENT ON COLUMN transactions.source_reward_id IS 'Reference to the source reward transaction for REDEEM and EXPIRY transactions. NULL for REWARD transactions.';
