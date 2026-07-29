-- Query indexes from PHYSICAL_SCHEMA.md IX-001 through IX-036.

CREATE INDEX ix_classrooms_school_status
    ON classrooms (school_id, status);
CREATE INDEX ix_classroom_students_classroom_status
    ON classroom_students (classroom_id, status, roster_number);
CREATE INDEX ix_student_sessions_expires
    ON student_sessions (expires_at)
    WHERE status = 'ACTIVE';
CREATE INDEX ix_classroom_modules_status
    ON classroom_modules (classroom_id, status, module_code);
CREATE INDEX ix_autonomous_schedules_lookup
    ON autonomous_schedules (classroom_id, day_of_week, local_start_time)
    WHERE enabled = true;
CREATE INDEX ix_payslips_student_issued
    ON payslips (classroom_student_id, issued_at DESC);
CREATE INDEX ix_transaction_groups_classroom_committed
    ON transaction_groups (classroom_id, committed_at DESC, id)
    WHERE status = 'COMMITTED';
CREATE INDEX ix_transaction_groups_type_status
    ON transaction_groups (classroom_id, transaction_type, status, created_at DESC);
CREATE INDEX ix_transaction_groups_recovery
    ON transaction_groups (recovery_of_group_id)
    WHERE recovery_of_group_id IS NOT NULL;
CREATE INDEX ix_ledger_entries_account_sequence
    ON ledger_entries (ledger_account_id, account_sequence DESC);
CREATE INDEX ix_ledger_entries_group
    ON ledger_entries (transaction_group_id);
CREATE INDEX ix_idempotency_expires
    ON idempotency_records (expires_at);
CREATE INDEX ix_state_transitions_aggregate
    ON state_transitions (classroom_id, aggregate_type, aggregate_id, state_version DESC);
CREATE INDEX ix_outbox_unpublished
    ON outbox_events (occurred_at, event_id)
    WHERE published_at IS NULL;
CREATE INDEX ix_audit_events_classroom_time
    ON audit_events (classroom_id, occurred_at DESC, id);
CREATE INDEX ix_store_products_classroom_status
    ON store_products (classroom_id, status, product_kind);
CREATE INDEX ix_store_orders_student_time
    ON store_orders (classroom_student_id, created_at DESC);
CREATE INDEX ix_store_orders_fulfillment
    ON store_orders (classroom_id, status, created_at);
CREATE INDEX ix_inventory_items_owner_status
    ON inventory_items (classroom_student_id, status, acquired_at DESC);
CREATE INDEX ix_trades_proposer_status
    ON trades (proposer_student_id, status, created_at DESC);
CREATE INDEX ix_trades_counterpart_status
    ON trades (counterpart_student_id, status, created_at DESC);
CREATE INDEX ix_trades_teacher_queue
    ON trades (classroom_id, status, created_at)
    WHERE status = 'AWAITING_TEACHER';
CREATE INDEX ix_deposit_accounts_maturity
    ON deposit_accounts (classroom_id, status, matures_at);
CREATE INDEX ix_savings_installments_due
    ON savings_installments (classroom_id, status, due_at);
CREATE INDEX ix_investment_price_steps_reveal
    ON investment_price_steps (investment_asset_id, step_number DESC);
CREATE INDEX ix_investment_orders_student_time
    ON investment_orders (classroom_student_id, submitted_at DESC);
CREATE INDEX ix_loan_applications_queue
    ON loan_applications (classroom_id, status, created_at);
CREATE INDEX ix_loan_installments_due
    ON loan_installments (classroom_id, status, due_at);
CREATE INDEX ix_credit_events_student_time
    ON credit_events (classroom_student_id, occurred_at DESC);
CREATE INDEX ix_activity_sessions_classroom_status
    ON activity_sessions (classroom_id, status, starts_at DESC);
CREATE INDEX ix_quiz_submissions_session_student
    ON quiz_submissions (quiz_session_id, classroom_student_id);
CREATE INDEX ix_quiz_results_session_version
    ON quiz_result_versions (quiz_session_id, version DESC);
CREATE INDEX ix_vote_ballots_vote_option
    ON vote_ballots (vote_id, vote_option_id);
CREATE INDEX ix_ai_runs_classroom_status
    ON ai_runs (classroom_id, status, requested_at DESC);
CREATE INDEX ix_async_operations_status
    ON async_operations (classroom_id, status, created_at);
CREATE INDEX ix_report_snapshots_retention
    ON report_snapshots (retention_until);

-- PostgreSQL does not create indexes for foreign-key columns automatically.
CREATE INDEX ix_classroom_teachers_user ON classroom_teachers (user_id);
CREATE INDEX ix_classroom_students_salary_role ON classroom_students (salary_role_id);
CREATE INDEX ix_student_sessions_device ON student_sessions (device_link_id);
CREATE INDEX ix_policy_snapshot_sources_snapshot ON policy_snapshot_sources (policy_snapshot_id);
CREATE INDEX ix_transaction_inquiries_group ON transaction_inquiries (transaction_group_id);
CREATE INDEX ix_store_order_items_order ON store_order_items (store_order_id);
CREATE INDEX ix_inventory_items_locked_trade ON inventory_items (locked_trade_id);
CREATE INDEX ix_trade_item_legs_inventory ON trade_item_legs (inventory_item_id);
CREATE INDEX ix_savings_installments_account ON savings_installments (savings_account_id);
CREATE INDEX ix_investment_assets_scenario ON investment_assets (scenario_version_id);
CREATE INDEX ix_loan_installments_loan ON loan_installments (loan_id);
CREATE INDEX ix_activity_participants_session ON activity_participants (activity_session_id);
CREATE INDEX ix_quiz_answers_submission ON quiz_answers (quiz_submission_id);
CREATE INDEX ix_ai_artifacts_run ON ai_artifacts (ai_run_id);

-- Public-display projections deliberately exclude balances, credit, debt,
-- TEST answers, eligibility tokens, and peer-trade details.
CREATE VIEW public_quiz_leaderboard AS
SELECT
    result.classroom_id,
    result.quiz_session_id,
    student.nickname,
    student_result.score,
    student_result.correct_count,
    student_result.rank,
    student_result.total_response_time_ms
FROM quiz_result_versions result
JOIN quiz_sessions session
  ON session.classroom_id = result.classroom_id
 AND session.activity_session_id = result.quiz_session_id
JOIN quiz_student_results student_result
  ON student_result.classroom_id = result.classroom_id
 AND student_result.quiz_result_version_id = result.id
JOIN classroom_students student
  ON student.classroom_id = student_result.classroom_id
 AND student.id = student_result.classroom_student_id
WHERE session.mode = 'GAME'
  AND result.published_at IS NOT NULL;

CREATE VIEW public_vote_totals AS
SELECT
    option.classroom_id,
    option.vote_id,
    option.id AS vote_option_id,
    option.label,
    count(ballot.id) AS ballot_count
FROM vote_options option
LEFT JOIN vote_ballots ballot
  ON ballot.classroom_id = option.classroom_id
 AND ballot.vote_id = option.vote_id
 AND ballot.vote_option_id = option.id
GROUP BY option.classroom_id, option.vote_id, option.id, option.label;

CREATE VIEW public_classroom_fund_balances AS
SELECT classroom_id, current_balance
FROM ledger_accounts
WHERE account_type = 'CLASSROOM_FUND'
  AND status = 'ACTIVE';

CREATE VIEW public_investment_prices AS
SELECT
    asset.classroom_id,
    asset.symbol,
    asset.name,
    step.step_number,
    step.price,
    step.change_rate_bps,
    step.revealed_at
FROM investment_assets asset
JOIN investment_price_steps step
  ON step.classroom_id = asset.classroom_id
 AND step.investment_asset_id = asset.id
WHERE step.revealed_at IS NOT NULL;
