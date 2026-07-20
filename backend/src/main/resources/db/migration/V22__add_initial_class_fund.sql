ALTER TABLE room_rules
    ADD COLUMN initial_fund_amount BIGINT NOT NULL DEFAULT 0 CHECK (initial_fund_amount >= 0);
