CREATE TABLE financial_settlement_items (
    id UUID PRIMARY KEY,
    settlement_run_id UUID NOT NULL REFERENCES settlement_runs(id),
    student_id UUID NOT NULL REFERENCES students(id),
    wallet_before BIGINT NOT NULL,
    deposit_principal BIGINT NOT NULL DEFAULT 0 CHECK (deposit_principal >= 0),
    deposit_interest BIGINT NOT NULL DEFAULT 0 CHECK (deposit_interest >= 0),
    investment_principal BIGINT NOT NULL DEFAULT 0 CHECK (investment_principal >= 0),
    investment_profit BIGINT NOT NULL DEFAULT 0,
    investment_value BIGINT NOT NULL DEFAULT 0 CHECK (investment_value >= 0),
    taxable_profit BIGINT NOT NULL DEFAULT 0 CHECK (taxable_profit >= 0),
    financial_tax BIGINT NOT NULL DEFAULT 0 CHECK (financial_tax >= 0),
    wallet_after BIGINT NOT NULL CHECK (wallet_after >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (settlement_run_id, student_id)
);

CREATE TABLE financial_settlement_allocations (
    id UUID PRIMARY KEY,
    settlement_item_id UUID NOT NULL REFERENCES financial_settlement_items(id),
    asset_id UUID NOT NULL REFERENCES investment_assets(id),
    principal BIGINT NOT NULL CHECK (principal > 0),
    return_bps INTEGER NOT NULL,
    profit BIGINT NOT NULL,
    final_value BIGINT NOT NULL CHECK (final_value >= 0),
    UNIQUE (settlement_item_id, asset_id)
);

CREATE INDEX idx_financial_settlement_items_student
    ON financial_settlement_items(student_id, settlement_run_id);
CREATE INDEX idx_financial_settlement_allocations_item
    ON financial_settlement_allocations(settlement_item_id);
