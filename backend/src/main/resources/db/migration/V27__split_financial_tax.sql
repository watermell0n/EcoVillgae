ALTER TABLE financial_settlement_items
    ADD COLUMN deposit_financial_tax BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN investment_financial_tax BIGINT NOT NULL DEFAULT 0;

UPDATE financial_settlement_items item
SET deposit_financial_tax = LEAST(
        item.financial_tax,
        FLOOR(item.deposit_interest * rules.financial_tax_bps / 10000.0)::BIGINT
    ),
    investment_financial_tax = item.financial_tax - LEAST(
        item.financial_tax,
        FLOOR(item.deposit_interest * rules.financial_tax_bps / 10000.0)::BIGINT
    )
FROM settlement_runs run
JOIN room_rules rules ON rules.room_id = run.room_id
WHERE run.id = item.settlement_run_id;

ALTER TABLE financial_settlement_items
    ADD CONSTRAINT chk_financial_settlement_deposit_tax
        CHECK (deposit_financial_tax >= 0),
    ADD CONSTRAINT chk_financial_settlement_investment_tax
        CHECK (investment_financial_tax >= 0);
