CREATE TEMP TABLE day1_income_item_adjustments ON COMMIT DROP AS
WITH base AS (
    SELECT
        item.id AS item_id,
        run.id AS settlement_run_id,
        run.room_id,
        run.session_id,
        item.student_id,
        item.role_income AS old_role_income,
        item.participation_reward AS old_participation_reward,
        item.correct_reward AS old_correct_reward,
        item.gross_income AS old_gross_income,
        item.income_tax AS old_income_tax,
        item.net_income AS old_net_income,
        CASE
            WHEN item.quiz_submitted THEN (
                item.correct_count::BIGINT * 5 * item.reward_rate_bps
            ) / 10_000
            ELSE 0
        END AS new_correct_reward
    FROM settlement_items item
    JOIN settlement_runs run ON run.id = item.settlement_run_id
    WHERE run.settlement_type = 'DAY1_INCOME'
      AND run.status = 'COMPLETED'
), calculated AS (
    SELECT
        base.*,
        150::BIGINT AS new_role_income,
        0::BIGINT AS new_participation_reward,
        150::BIGINT + base.new_correct_reward AS new_gross_income
    FROM base
), finalized AS (
    SELECT
        calculated.*,
        calculated.new_gross_income * 1_000 / 10_000 AS new_income_tax
    FROM calculated
)
SELECT
    finalized.*,
    finalized.new_gross_income - finalized.new_income_tax AS new_net_income,
    finalized.new_gross_income - finalized.old_gross_income AS gross_delta,
    finalized.new_income_tax - finalized.old_income_tax AS tax_delta,
    (finalized.new_gross_income - finalized.new_income_tax)
        - finalized.old_net_income AS net_delta
FROM finalized
WHERE finalized.old_role_income <> finalized.new_role_income
   OR finalized.old_participation_reward <> finalized.new_participation_reward
   OR finalized.old_correct_reward <> finalized.new_correct_reward
   OR finalized.old_gross_income <> finalized.new_gross_income
   OR finalized.old_income_tax <> finalized.new_income_tax
   OR finalized.old_net_income <>
        finalized.new_gross_income - finalized.new_income_tax;

CREATE TEMP TABLE day1_income_room_adjustments ON COMMIT DROP AS
SELECT
    settlement_run_id,
    room_id,
    session_id,
    SUM(gross_delta)::BIGINT AS gross_delta,
    SUM(tax_delta)::BIGINT AS tax_delta,
    SUM(net_delta)::BIGINT AS net_delta
FROM day1_income_item_adjustments
GROUP BY settlement_run_id, room_id, session_id;

CREATE TEMP TABLE day1_income_adjustment_transactions ON COMMIT DROP AS
SELECT
    gen_random_uuid() AS transaction_id,
    adjustment.settlement_run_id,
    adjustment.room_id,
    adjustment.session_id,
    adjustment.gross_delta,
    adjustment.tax_delta,
    adjustment.net_delta,
    treasury.id AS treasury_account_id,
    treasury.balance AS treasury_balance_before,
    class_fund.id AS class_fund_account_id,
    class_fund.balance AS class_fund_balance_before
FROM day1_income_room_adjustments adjustment
JOIN accounts treasury
  ON treasury.room_id = adjustment.room_id
 AND treasury.student_id IS NULL
 AND treasury.account_type = 'SYSTEM_TREASURY'
JOIN accounts class_fund
  ON class_fund.room_id = adjustment.room_id
 AND class_fund.student_id IS NULL
 AND class_fund.account_type = 'CLASS_FUND';

INSERT INTO financial_transactions (
    id,
    room_id,
    session_id,
    student_id,
    actor_id,
    settlement_run_id,
    transaction_type,
    status,
    actor_type,
    idempotency_key,
    reason_code,
    description,
    metadata,
    created_at,
    completed_at
)
SELECT
    adjustment.transaction_id,
    adjustment.room_id,
    adjustment.session_id,
    NULL,
    NULL,
    adjustment.settlement_run_id,
    'DAY1_INCOME_POLICY_ADJUSTMENT',
    'SUCCESS',
    'SYSTEM',
    'migration:v19:day1-income:' || adjustment.room_id,
    'POLICY_CHANGE',
    '역할 기본 월급 및 퀴즈 정답수당 정책 변경 보정',
    jsonb_build_object(
        'roleIncome', 150,
        'participationReward', 0,
        'correctRewardPerQuestion', 5,
        'incomeTaxBps', 1000,
        'grossDelta', adjustment.gross_delta,
        'taxDelta', adjustment.tax_delta,
        'netDelta', adjustment.net_delta
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM day1_income_adjustment_transactions adjustment;

WITH wallet_entries AS (
    SELECT
        transaction.transaction_id,
        wallet.id AS account_id,
        item.net_delta AS amount,
        wallet.balance AS balance_before,
        wallet.balance + item.net_delta AS balance_after,
        ROW_NUMBER() OVER (
            PARTITION BY item.room_id
            ORDER BY item.student_id
        )::INTEGER AS entry_order
    FROM day1_income_item_adjustments item
    JOIN day1_income_adjustment_transactions transaction
      ON transaction.room_id = item.room_id
    JOIN accounts wallet
      ON wallet.room_id = item.room_id
     AND wallet.student_id = item.student_id
     AND wallet.account_type = 'STUDENT_WALLET'
    WHERE item.net_delta <> 0
)
INSERT INTO financial_entries (
    id,
    transaction_id,
    account_id,
    amount,
    balance_before,
    balance_after,
    entry_order,
    created_at
)
SELECT
    gen_random_uuid(),
    transaction_id,
    account_id,
    amount,
    balance_before,
    balance_after,
    entry_order,
    CURRENT_TIMESTAMP
FROM wallet_entries;

INSERT INTO financial_entries (
    id,
    transaction_id,
    account_id,
    amount,
    balance_before,
    balance_after,
    entry_order,
    created_at
)
SELECT
    gen_random_uuid(),
    transaction.transaction_id,
    transaction.treasury_account_id,
    -transaction.gross_delta,
    transaction.treasury_balance_before,
    transaction.treasury_balance_before - transaction.gross_delta,
    (
        SELECT COUNT(*)
        FROM day1_income_item_adjustments item
        WHERE item.room_id = transaction.room_id
          AND item.net_delta <> 0
    )::INTEGER + 1,
    CURRENT_TIMESTAMP
FROM day1_income_adjustment_transactions transaction
WHERE transaction.gross_delta <> 0;

INSERT INTO financial_entries (
    id,
    transaction_id,
    account_id,
    amount,
    balance_before,
    balance_after,
    entry_order,
    created_at
)
SELECT
    gen_random_uuid(),
    transaction.transaction_id,
    transaction.class_fund_account_id,
    transaction.tax_delta,
    transaction.class_fund_balance_before,
    transaction.class_fund_balance_before + transaction.tax_delta,
    (
        SELECT COUNT(*)
        FROM day1_income_item_adjustments item
        WHERE item.room_id = transaction.room_id
          AND item.net_delta <> 0
    )::INTEGER + 2,
    CURRENT_TIMESTAMP
FROM day1_income_adjustment_transactions transaction
WHERE transaction.tax_delta <> 0;

UPDATE accounts wallet
SET balance = wallet.balance + item.net_delta,
    version = wallet.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM day1_income_item_adjustments item
WHERE wallet.room_id = item.room_id
  AND wallet.student_id = item.student_id
  AND wallet.account_type = 'STUDENT_WALLET'
  AND item.net_delta <> 0;

UPDATE accounts treasury
SET balance = treasury.balance - adjustment.gross_delta,
    version = treasury.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM day1_income_room_adjustments adjustment
WHERE treasury.room_id = adjustment.room_id
  AND treasury.student_id IS NULL
  AND treasury.account_type = 'SYSTEM_TREASURY'
  AND adjustment.gross_delta <> 0;

UPDATE accounts class_fund
SET balance = class_fund.balance + adjustment.tax_delta,
    version = class_fund.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM day1_income_room_adjustments adjustment
WHERE class_fund.room_id = adjustment.room_id
  AND class_fund.student_id IS NULL
  AND class_fund.account_type = 'CLASS_FUND'
  AND adjustment.tax_delta <> 0;

UPDATE settlement_items item
SET role_income = adjustment.new_role_income,
    participation_reward = adjustment.new_participation_reward,
    correct_reward = adjustment.new_correct_reward,
    gross_income = adjustment.new_gross_income,
    income_tax = adjustment.new_income_tax,
    net_income = adjustment.new_net_income
FROM day1_income_item_adjustments adjustment
WHERE item.id = adjustment.item_id;

UPDATE settlement_runs run
SET summary = totals.summary
FROM (
    SELECT
        item.settlement_run_id,
        jsonb_build_object(
            'totalGrossIncome', SUM(item.gross_income),
            'totalIncomeTax', SUM(item.income_tax),
            'totalNetIncome', SUM(item.net_income)
        ) AS summary
    FROM settlement_items item
    WHERE item.settlement_run_id IN (
        SELECT settlement_run_id
        FROM day1_income_room_adjustments
    )
    GROUP BY item.settlement_run_id
) totals
WHERE run.id = totals.settlement_run_id;

UPDATE rooms room
SET version = room.version + 1,
    sync_version = room.sync_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE room.id IN (
    SELECT room_id
    FROM day1_income_room_adjustments
);
