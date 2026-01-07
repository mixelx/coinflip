-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_user_id BIGINT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Balance table (1:1 with users)
CREATE TABLE balance (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    ton_nano BIGINT NOT NULL DEFAULT 0,
    usdt_micro BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Coinflip game table
CREATE TABLE coinflip_game (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    stake_nano BIGINT NOT NULL,
    chosen_side VARCHAR(8) NOT NULL CHECK (chosen_side IN ('HEADS', 'TAILS')),
    result_side VARCHAR(8) NOT NULL CHECK (result_side IN ('HEADS', 'TAILS')),
    win BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_coinflip_game_user_created ON coinflip_game(user_id, created_at DESC);

-- Deposit table
CREATE TABLE deposit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    asset VARCHAR(8) NOT NULL CHECK (asset IN ('TON', 'USDT')),
    amount BIGINT NOT NULL,
    tx_hash VARCHAR(128) NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_deposit_user_created ON deposit(user_id, created_at DESC);

-- Withdraw request table
CREATE TABLE withdraw_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    asset VARCHAR(8) NOT NULL CHECK (asset IN ('TON', 'USDT')),
    amount BIGINT NOT NULL,
    to_address VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_withdraw_request_user_created ON withdraw_request(user_id, created_at DESC);
CREATE INDEX idx_withdraw_request_status_created ON withdraw_request(status, created_at ASC);

