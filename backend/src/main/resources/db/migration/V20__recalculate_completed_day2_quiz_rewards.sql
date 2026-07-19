CREATE TEMP TABLE day2_quiz_item_adjustments ON COMMIT DROP AS
WITH calculated AS (
    SELECT
        item.id AS item_id,
        run.id AS settlement_run_id,
        run.room_id,
        run.session_id,
        item.student_id,
        item.participation_reward AS old_participation_reward,
        item.correct_reward AS old_correct_reward,
        item.gross_income AS old_total_reward,
        item.net_income AS old_net_reward,
        CASE
            WHEN item.quiz_submitted THEN (
                item.correct_count::BIGINT * 5 * item.reward_rate_bps
            ) / 10_000
            ELSE 0
        END AS new_correct_reward
    FROM settlement_items item
    JOIN settlement_runs run ON run.id = item.settlement_run_id
    WHERE run.settlement_type = 'DAY2_QUIZ_REWARD'
      AND run.status = 'COMPLETED'
)
SELECT
    calculated.*,
    0::BIGINT AS new_participation_reward,
    calculated.new_correct_reward AS new_total_reward,
    calculated.new_correct_reward - calculated.old_net_reward AS reward_delta
FROM calculated
WHERE calculated.old_participation_reward <> 0
   OR calculated.old_correct_reward <> calculated.new_correct_reward
   OR calculated.old_total_reward <> calculated.new_correct_reward
   OR calculated.old_net_reward <> calculated.new_correct_reward;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM day2_quiz_item_adjustments adjustment
        JOIN accounts wallet
          ON wallet.room_id = adjustment.room_id
         AND wallet.student_id = adjustment.student_id
         AND wallet.account_type = 'STUDENT_WALLET'
        WHERE wallet.balance + adjustment.reward_delta < 0
    ) THEN
        RAISE EXCEPTION '2일차 퀴즈 보상 보정 후 학생 지갑 잔액이 음수가 됩니다.';
    END IF;
END $$;

CREATE TEMP TABLE day2_quiz_room_adjustments ON COMMIT DROP AS
SELECT
    settlement_run_id,
    room_id,
    session_id,
    SUM(reward_delta)::BIGINT AS reward_delta
FROM day2_quiz_item_adjustments
GROUP BY settlement_run_id, room_id, session_id;

CREATE TEMP TABLE day2_quiz_adjustment_transactions ON COMMIT DROP AS
SELECT
    gen_random_uuid() AS transaction_id,
    adjustment.settlement_run_id,
    adjustment.room_id,
    adjustment.session_id,
    adjustment.reward_delta,
    treasury.id AS treasury_account_id,
    treasury.balance AS treasury_balance_before
FROM day2_quiz_room_adjustments adjustment
JOIN accounts treasury
  ON treasury.room_id = adjustment.room_id
 AND treasury.student_id IS NULL
 AND treasury.account_type = 'SYSTEM_TREASURY';

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
    'DAY2_QUIZ_POLICY_ADJUSTMENT',
    'SUCCESS',
    'SYSTEM',
    'migration:v20:day2-quiz:' || adjustment.room_id,
    'POLICY_CHANGE',
    '2일차 퀴즈 참여수당 제거 정책 보정',
    jsonb_build_object(
        'participationReward', 0,
        'correctRewardPerQuestion', 5,
        'incomeTax', 0,
        'rewardDelta', adjustment.reward_delta
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM day2_quiz_adjustment_transactions adjustment;

WITH wallet_entries AS (
    SELECT
        transaction.transaction_id,
        wallet.id AS account_id,
        item.reward_delta AS amount,
        wallet.balance AS balance_before,
        wallet.balance + item.reward_delta AS balance_after,
        ROW_NUMBER() OVER (
            PARTITION BY item.room_id
            ORDER BY item.student_id
        )::INTEGER AS entry_order
    FROM day2_quiz_item_adjustments item
    JOIN day2_quiz_adjustment_transactions transaction
      ON transaction.room_id = item.room_id
    JOIN accounts wallet
      ON wallet.room_id = item.room_id
     AND wallet.student_id = item.student_id
     AND wallet.account_type = 'STUDENT_WALLET'
    WHERE item.reward_delta <> 0
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
    -transaction.reward_delta,
    transaction.treasury_balance_before,
    transaction.treasury_balance_before - transaction.reward_delta,
    (
        SELECT COUNT(*)
        FROM day2_quiz_item_adjustments item
        WHERE item.room_id = transaction.room_id
          AND item.reward_delta <> 0
    )::INTEGER + 1,
    CURRENT_TIMESTAMP
FROM day2_quiz_adjustment_transactions transaction
WHERE transaction.reward_delta <> 0;

UPDATE accounts wallet
SET balance = wallet.balance + item.reward_delta,
    version = wallet.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM day2_quiz_item_adjustments item
WHERE wallet.room_id = item.room_id
  AND wallet.student_id = item.student_id
  AND wallet.account_type = 'STUDENT_WALLET'
  AND item.reward_delta <> 0;

UPDATE accounts treasury
SET balance = treasury.balance - adjustment.reward_delta,
    version = treasury.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM day2_quiz_room_adjustments adjustment
WHERE treasury.room_id = adjustment.room_id
  AND treasury.student_id IS NULL
  AND treasury.account_type = 'SYSTEM_TREASURY'
  AND adjustment.reward_delta <> 0;

UPDATE settlement_items item
SET role_income = 0,
    participation_reward = adjustment.new_participation_reward,
    correct_reward = adjustment.new_correct_reward,
    gross_income = adjustment.new_total_reward,
    income_tax = 0,
    net_income = adjustment.new_total_reward
FROM day2_quiz_item_adjustments adjustment
WHERE item.id = adjustment.item_id;

UPDATE settlement_runs run
SET summary = totals.summary
FROM (
    SELECT
        item.settlement_run_id,
        jsonb_build_object(
            'totalReward', SUM(item.net_income),
            'submittedCount', COUNT(*) FILTER (WHERE item.quiz_submitted)
        ) AS summary
    FROM settlement_items item
    WHERE item.settlement_run_id IN (
        SELECT settlement_run_id
        FROM day2_quiz_room_adjustments
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
    FROM day2_quiz_room_adjustments
);
