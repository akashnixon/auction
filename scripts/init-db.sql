CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    is_selling BOOLEAN NOT NULL,
    is_highest_bidder BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deregistered_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_users_username
ON users (username);

CREATE TABLE IF NOT EXISTS auctions (
    auction_id VARCHAR(64) PRIMARY KEY,
    item_name VARCHAR(255) NOT NULL,
    seller_id VARCHAR(64) NOT NULL,
    image_data_url TEXT,
    starting_price NUMERIC(18, 2) NOT NULL DEFAULT 0,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    cycle_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    winning_bid_id VARCHAR(64),
    winner_user_id VARCHAR(64),
    finalized_at TIMESTAMPTZ
);

ALTER TABLE auctions
ADD COLUMN IF NOT EXISTS image_data_url TEXT;

ALTER TABLE auctions
ADD COLUMN IF NOT EXISTS starting_price NUMERIC(18, 2) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_auctions_status_end_time
ON auctions (status, end_time);

CREATE TABLE IF NOT EXISTS bids (
    bid_id VARCHAR(64) PRIMARY KEY,
    auction_id VARCHAR(64) NOT NULL,
    cycle_number INT NOT NULL,
    bidder_id VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    CONSTRAINT uk_bid_idempotency UNIQUE (auction_id, bidder_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_bids_auction_cycle_winner
ON bids (auction_id, cycle_number, amount DESC, received_at ASC, bid_id ASC);
