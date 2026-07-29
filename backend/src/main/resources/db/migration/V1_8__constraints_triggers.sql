-- Cross-cutting uniqueness, domain checks, immutability, and deferred ledger integrity.

-- Partial and functional uniqueness.
CREATE UNIQUE INDEX ux_school_memberships_active_role
    ON school_memberships (school_id, user_id, role)
    WHERE status = 'ACTIVE' AND ended_at IS NULL;
CREATE UNIQUE INDEX ux_classroom_teachers_active_owner
    ON classroom_teachers (classroom_id)
    WHERE role = 'OWNER' AND ended_at IS NULL;
CREATE UNIQUE INDEX ux_classroom_teachers_active_role
    ON classroom_teachers (classroom_id, user_id, role)
    WHERE ended_at IS NULL;
CREATE UNIQUE INDEX ux_classroom_students_active_roster
    ON classroom_students (classroom_id, roster_number)
    WHERE retired_at IS NULL;
CREATE UNIQUE INDEX ux_classroom_students_active_nickname
    ON classroom_students (classroom_id, lower(nickname))
    WHERE retired_at IS NULL;
CREATE UNIQUE INDEX ux_student_sessions_active_student
    ON student_sessions (classroom_student_id)
    WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_ledger_accounts_active_student_wallet
    ON ledger_accounts (classroom_student_id)
    WHERE account_type = 'STUDENT_WALLET' AND status = 'ACTIVE';
CREATE UNIQUE INDEX ux_ledger_accounts_active_classroom_fund
    ON ledger_accounts (classroom_id, system_owner_key)
    WHERE account_type = 'CLASSROOM_FUND' AND status = 'ACTIVE';
CREATE UNIQUE INDEX ux_investment_markets_open
    ON investment_markets (classroom_id)
    WHERE status <> 'CLOSED';
CREATE UNIQUE INDEX ux_classroom_policy_versions_current
    ON classroom_policy_versions (classroom_id, policy_area)
    WHERE superseded_at IS NULL;
CREATE UNIQUE INDEX ux_salary_roles_name
    ON salary_roles (classroom_id, lower(name));
CREATE UNIQUE INDEX ux_store_products_name_kind
    ON store_products (classroom_id, lower(name), product_kind);
CREATE UNIQUE INDEX ux_financial_products_type_name
    ON financial_products (classroom_id, product_type, lower(name));
CREATE UNIQUE INDEX ux_realtime_streams_resource
    ON realtime_streams (classroom_id, stream_type, coalesce(resource_id, '00000000-0000-0000-0000-000000000000'::uuid));

-- Status sets.
ALTER TABLE schools ADD CONSTRAINT ck_schools_status
    CHECK (status IN ('ACTIVE', 'INACTIVE'));
ALTER TABLE users ADD CONSTRAINT ck_users_status
    CHECK (status IN ('ACTIVE', 'INACTIVE'));
ALTER TABLE classrooms ADD CONSTRAINT ck_classrooms_status
    CHECK (status IN ('ACTIVE', 'CLOSING', 'READ_ONLY', 'ARCHIVED'));
ALTER TABLE classroom_modules ADD CONSTRAINT ck_classroom_modules_status
    CHECK (status IN ('DISABLED', 'ACTIVE', 'STOPPING'));
ALTER TABLE transaction_groups ADD CONSTRAINT ck_transaction_groups_status
    CHECK (status IN ('RECEIVED', 'VALIDATING', 'COMMITTED', 'REJECTED'));
ALTER TABLE transaction_inquiries ADD CONSTRAINT ck_transaction_inquiries_status
    CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED'));
ALTER TABLE trades ADD CONSTRAINT ck_trades_status
    CHECK (status IN (
        'DRAFT', 'AWAITING_COUNTERPART', 'AWAITING_TEACHER', 'SETTLING',
        'COMPLETED', 'CANCELED', 'DECLINED', 'EXPIRED', 'REJECTED', 'FAILED'
    ));
ALTER TABLE inventory_items ADD CONSTRAINT ck_inventory_items_status
    CHECK (status IN ('AVAILABLE', 'EQUIPPED', 'LOCKED_TRADE'));
ALTER TABLE store_orders ADD CONSTRAINT ck_store_orders_status
    CHECK (status IN (
        'REQUESTED', 'COMPLETED', 'PAID', 'FAILED', 'READY_FOR_PICKUP',
        'RECEIVED', 'OPERATION_CANCELED'
    ));
ALTER TABLE deposit_accounts ADD CONSTRAINT ck_deposit_accounts_status
    CHECK (status IN ('ACTIVE', 'MATURED', 'EARLY_TERMINATED', 'CLOSE_SETTLED'));
ALTER TABLE savings_accounts ADD CONSTRAINT ck_savings_accounts_status
    CHECK (status IN ('ACTIVE', 'MATURED', 'EARLY_TERMINATED', 'CLOSE_SETTLED'));
ALTER TABLE savings_installments ADD CONSTRAINT ck_savings_installments_status
    CHECK (status IN ('SCHEDULED', 'PAID', 'MISSED'));
ALTER TABLE investment_markets ADD CONSTRAINT ck_investment_markets_status
    CHECK (status IN ('DRAFT', 'OPEN', 'PAUSED', 'CLOSED'));
ALTER TABLE investment_orders ADD CONSTRAINT ck_investment_orders_status
    CHECK (status IN ('RECEIVED', 'VALIDATING', 'EXECUTED', 'REJECTED'));
ALTER TABLE loan_applications ADD CONSTRAINT ck_loan_applications_status
    CHECK (status IN ('APPLIED', 'APPROVED', 'REJECTED', 'CANCELED'));
ALTER TABLE loans ADD CONSTRAINT ck_loans_status
    CHECK (status IN ('ACTIVE', 'OVERDUE', 'REPAID', 'CLOSED_UNPAID', 'FAILED'));
ALTER TABLE credit_profiles ADD CONSTRAINT ck_credit_profiles_level
    CHECK (level IN ('NORMAL', 'GOOD', 'CAUTION', 'RESTRICTED'));
ALTER TABLE activity_sessions ADD CONSTRAINT ck_activity_sessions_status
    CHECK (status IN ('READY', 'LIVE', 'FINALIZING', 'COMPLETED', 'CANCELED', 'FAILED'));
ALTER TABLE quiz_question_versions ADD CONSTRAINT ck_quiz_question_versions_status
    CHECK (status IN ('DRAFT', 'REVIEW_REQUIRED', 'APPROVED', 'PUBLISHED', 'RETIRED'));
ALTER TABLE quiz_sessions ADD CONSTRAINT ck_quiz_sessions_status
    CHECK (status IN (
        'READY', 'OPEN', 'SCORING', 'REVIEWING',
        'RESULT_PUBLISHED', 'CLOSED', 'CANCELED'
    ));
ALTER TABLE quiz_question_issues ADD CONSTRAINT ck_quiz_question_issues_status
    CHECK (status IN ('VALID', 'UNDER_REVIEW', 'INVALIDATED'));
ALTER TABLE regrading_runs ADD CONSTRAINT ck_regrading_runs_status
    CHECK (status IN ('REQUESTED', 'RUNNING', 'COMPLETED', 'FAILED'));
ALTER TABLE votes ADD CONSTRAINT ck_votes_status
    CHECK (status IN ('READY', 'OPEN', 'CLOSED', 'RESULT_PUBLISHED'));
ALTER TABLE ai_runs ADD CONSTRAINT ck_ai_runs_status
    CHECK (status IN (
        'REQUESTED', 'DATA_CHECK', 'INSUFFICIENT_DATA', 'GENERATING', 'VALIDATING',
        'AWAITING_TEACHER', 'APPROVED', 'REJECTED', 'EXPIRED', 'APPLYING',
        'COMPLETED', 'TIMED_OUT', 'FAILED', 'REJECTED_OUTPUT', 'APPLY_FAILED'
    ));
ALTER TABLE ai_proposals ADD CONSTRAINT ck_ai_proposals_status
    CHECK (status IN (
        'AWAITING_TEACHER', 'APPROVED', 'REJECTED',
        'EXPIRED', 'APPLIED', 'APPLY_FAILED'
    ));
ALTER TABLE economic_event_drafts ADD CONSTRAINT ck_economic_event_drafts_status
    CHECK (status IN ('DRAFT', 'APPROVED', 'SCHEDULED', 'APPLIED', 'CANCELED'));

-- CK-001 through CK-035 domain invariants.
ALTER TABLE course_templates ADD CONSTRAINT ck_course_templates_grade_range
    CHECK (grade_min BETWEEN 4 AND 6 AND grade_max BETWEEN grade_min AND 6);
ALTER TABLE classrooms ADD CONSTRAINT ck_classrooms_grade
    CHECK (grade BETWEEN 4 AND 6);
ALTER TABLE classrooms ADD CONSTRAINT ck_classrooms_dates
    CHECK (actual_ends_on IS NULL OR actual_starts_on IS NULL OR actual_ends_on >= actual_starts_on);
ALTER TABLE autonomous_schedules ADD CONSTRAINT ck_autonomous_schedules_time
    CHECK (day_of_week BETWEEN 1 AND 7 AND local_end_time > local_start_time);
ALTER TABLE salary_roles ADD CONSTRAINT ck_salary_roles_amount
    CHECK (gross_amount >= 0);
ALTER TABLE payslips ADD CONSTRAINT ck_payslips_amounts
    CHECK (
        gross_amount >= 0 AND deduction_amount >= 0 AND tax_amount >= 0 AND net_amount >= 0
        AND gross_amount = deduction_amount + tax_amount + net_amount
    );
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_accounts_owner
    CHECK (
        (owner_type = 'STUDENT' AND classroom_student_id IS NOT NULL AND system_owner_key IS NULL)
        OR
        (owner_type = 'SYSTEM' AND classroom_student_id IS NULL AND system_owner_key IS NOT NULL)
    );
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_accounts_balance
    CHECK (account_type <> 'STUDENT_WALLET' OR current_balance >= 0);
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_accounts_sequence
    CHECK (next_entry_sequence >= 1);
ALTER TABLE ledger_entries ADD CONSTRAINT ck_ledger_entries_amount_balance
    CHECK (amount <> 0 AND balance_before + amount = balance_after AND account_sequence >= 1);
ALTER TABLE transaction_groups ADD CONSTRAINT ck_transaction_groups_recovery
    CHECK (
        (recovery_of_group_id IS NULL AND root_transaction_group_id = id)
        OR
        (recovery_of_group_id IS NOT NULL
            AND recovery_of_group_id <> id
            AND root_transaction_group_id IS NOT NULL
            AND root_transaction_group_id <> id)
    );
ALTER TABLE store_product_versions ADD CONSTRAINT ck_store_product_versions_values
    CHECK (unit_price >= 0 AND vat_rate_bps BETWEEN 0 AND 10000);
ALTER TABLE store_stocks ADD CONSTRAINT ck_store_stocks_quantities
    CHECK (available_quantity >= 0 AND reserved_quantity >= 0);
ALTER TABLE store_orders ADD CONSTRAINT ck_store_orders_amounts
    CHECK (
        subtotal_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0
        AND subtotal_amount + tax_amount = total_amount
    );
ALTER TABLE store_order_items ADD CONSTRAINT ck_store_order_items_values
    CHECK (
        quantity > 0 AND unit_price >= 0 AND vat_rate_bps BETWEEN 0 AND 10000
        AND subtotal_amount = unit_price * quantity
        AND tax_amount >= 0 AND total_amount = subtotal_amount + tax_amount
    );
ALTER TABLE inventory_items ADD CONSTRAINT ck_inventory_items_lock_equip
    CHECK (
        (status = 'LOCKED_TRADE') = (locked_trade_id IS NOT NULL)
        AND (status = 'EQUIPPED') = (equipped_at IS NOT NULL)
    );
ALTER TABLE trades ADD CONSTRAINT ck_trades_distinct_students
    CHECK (proposer_student_id <> counterpart_student_id);
ALTER TABLE trade_money_legs ADD CONSTRAINT ck_trade_money_legs_values
    CHECK (amount > 0 AND from_student_id <> to_student_id);
ALTER TABLE trade_item_legs ADD CONSTRAINT ck_trade_item_legs_students
    CHECK (from_student_id <> to_student_id);
ALTER TABLE financial_product_versions ADD CONSTRAINT ck_financial_product_versions_values
    CHECK (
        term_units > 0
        AND interest_rate_bps BETWEEN 0 AND 10000
        AND early_termination_rate_bps BETWEEN 0 AND 10000
        AND (installment_count IS NULL OR installment_count > 0)
        AND (installment_amount IS NULL OR installment_amount > 0)
        AND minimum_amount > 0 AND maximum_amount >= minimum_amount
    );
ALTER TABLE deposit_accounts ADD CONSTRAINT ck_deposit_accounts_values
    CHECK (
        principal_amount > 0 AND expected_interest_amount >= 0
        AND matures_at >= opened_at
    );
ALTER TABLE savings_accounts ADD CONSTRAINT ck_savings_accounts_values
    CHECK (
        installment_amount > 0 AND installment_count > 0
        AND paid_installment_count BETWEEN 0 AND installment_count
        AND principal_balance >= 0 AND expected_interest_amount >= 0
        AND matures_at >= opened_at
    );
ALTER TABLE savings_installments ADD CONSTRAINT ck_savings_installments_values
    CHECK (
        installment_number > 0 AND scheduled_amount > 0
        AND retryable_until >= due_at
    );
ALTER TABLE investment_price_steps ADD CONSTRAINT ck_investment_price_steps_values
    CHECK (
        step_number >= 0 AND price > 0
        AND change_rate_bps BETWEEN -10000 AND 100000
    );
ALTER TABLE investment_orders ADD CONSTRAINT ck_investment_orders_values
    CHECK (
        side IN ('BUY', 'SELL') AND quantity > 0
        AND (executed_price IS NULL OR executed_price > 0)
        AND (executed_price_step IS NULL OR executed_price_step >= 0)
    );
ALTER TABLE investment_positions ADD CONSTRAINT ck_investment_positions_values
    CHECK (quantity >= 0 AND average_price >= 0);
ALTER TABLE loan_policy_versions ADD CONSTRAINT ck_loan_policy_versions_values
    CHECK (
        maximum_principal > 0 AND interest_rate_bps BETWEEN 0 AND 10000
        AND term_units > 0 AND installment_count > 0 AND grace_units >= 0
    );
ALTER TABLE loan_applications ADD CONSTRAINT ck_loan_applications_values
    CHECK (
        requested_amount > 0 AND (approved_amount IS NULL OR approved_amount > 0)
        AND credit_score_snapshot BETWEEN 0 AND 1000
    );
ALTER TABLE loans ADD CONSTRAINT ck_loans_values
    CHECK (
        principal_amount > 0 AND applied_interest_rate_bps BETWEEN 0 AND 10000
        AND outstanding_principal BETWEEN 0 AND principal_amount
        AND outstanding_interest >= 0 AND matures_at >= started_at
    );
ALTER TABLE loan_installments ADD CONSTRAINT ck_loan_installments_values
    CHECK (
        installment_number > 0
        AND scheduled_principal >= 0 AND scheduled_interest >= 0
        AND paid_principal BETWEEN 0 AND scheduled_principal
        AND paid_interest BETWEEN 0 AND scheduled_interest
        AND grace_until >= due_at
    );
ALTER TABLE credit_profiles ADD CONSTRAINT ck_credit_profiles_score
    CHECK (score BETWEEN 0 AND 1000);
ALTER TABLE credit_events ADD CONSTRAINT ck_credit_events_score
    CHECK (
        score_before BETWEEN 0 AND 1000 AND score_after BETWEEN 0 AND 1000
        AND score_before + score_delta = score_after
    );
ALTER TABLE quiz_session_questions ADD CONSTRAINT ck_quiz_session_questions_values
    CHECK (
        sequence_number > 0 AND score_value >= 0
        AND (time_limit_seconds IS NULL OR time_limit_seconds > 0)
    );
ALTER TABLE quiz_answers ADD CONSTRAINT ck_quiz_answers_values
    CHECK (
        NOT (selected_option_key IS NOT NULL AND free_text IS NOT NULL)
        AND (score_awarded IS NULL OR score_awarded >= 0)
        AND (response_time_ms IS NULL OR response_time_ms >= 0)
    );
ALTER TABLE quiz_student_results ADD CONSTRAINT ck_quiz_student_results_values
    CHECK (
        score >= 0 AND correct_count >= 0 AND reward_amount >= 0
        AND (rank IS NULL OR rank >= 1)
        AND (total_response_time_ms IS NULL OR total_response_time_ms >= 0)
    );
ALTER TABLE vote_options ADD CONSTRAINT ck_vote_options_values
    CHECK (sort_order >= 0 AND (budget_amount IS NULL OR budget_amount >= 0));
ALTER TABLE ai_runs ADD CONSTRAINT ck_ai_runs_retention
    CHECK (retention_until >= requested_at);
ALTER TABLE async_operations ADD CONSTRAINT ck_async_operations_progress
    CHECK (progress_percent BETWEEN 0 AND 100);
ALTER TABLE report_snapshots ADD CONSTRAINT ck_report_snapshots_retention
    CHECK (retention_until >= as_of AND retention_until >= created_at);
ALTER TABLE export_operations ADD CONSTRAINT ck_export_operations_retention
    CHECK (retention_until >= created_at);
ALTER TABLE audit_events ADD CONSTRAINT ck_audit_events_retention
    CHECK (retention_until >= occurred_at);
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_events_sequence
    CHECK (channel_sequence >= 1 AND state_version >= 0 AND publish_attempts >= 0);
ALTER TABLE realtime_streams ADD CONSTRAINT ck_realtime_streams_sequences
    CHECK (next_sequence >= 1 AND retention_from_sequence >= 1);

-- Ballot option and eligibility must belong to the same vote.
ALTER TABLE vote_options
    ADD CONSTRAINT uq_vote_options_vote_scope UNIQUE (classroom_id, vote_id, id);
ALTER TABLE vote_eligibilities
    ADD CONSTRAINT uq_vote_eligibilities_vote_token_scope
    UNIQUE (classroom_id, vote_id, eligibility_token_hash);
ALTER TABLE vote_ballots
    ADD CONSTRAINT fk_vote_ballots_vote_option
    FOREIGN KEY (classroom_id, vote_id, vote_option_id)
    REFERENCES vote_options (classroom_id, vote_id, id) ON DELETE RESTRICT;
ALTER TABLE vote_ballots
    ADD CONSTRAINT fk_vote_ballots_eligibility
    FOREIGN KEY (classroom_id, vote_id, eligibility_token_hash)
    REFERENCES vote_eligibilities (classroom_id, vote_id, eligibility_token_hash)
    ON DELETE RESTRICT;

-- Recovery roots are canonicalized before the check constraint is evaluated.
CREATE FUNCTION normalize_transaction_group_recovery()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    original_status varchar(40);
    original_root uuid;
BEGIN
    IF NEW.recovery_of_group_id IS NULL THEN
        IF NEW.root_transaction_group_id IS NULL THEN
            NEW.root_transaction_group_id := NEW.id;
        ELSIF NEW.root_transaction_group_id <> NEW.id THEN
            RAISE EXCEPTION 'original transaction root must equal its id';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.recovery_of_group_id = NEW.id THEN
        RAISE EXCEPTION 'transaction cannot recover itself';
    END IF;

    SELECT status, coalesce(root_transaction_group_id, id)
      INTO original_status, original_root
      FROM transaction_groups
     WHERE classroom_id = NEW.classroom_id
       AND id = NEW.recovery_of_group_id;

    IF NOT FOUND OR original_status <> 'COMMITTED' THEN
        RAISE EXCEPTION 'recovery target must be a committed transaction';
    END IF;

    IF NEW.root_transaction_group_id IS NOT NULL
       AND NEW.root_transaction_group_id <> original_root THEN
        RAISE EXCEPTION 'recovery transaction must preserve the original root';
    END IF;
    NEW.root_transaction_group_id := original_root;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transaction_groups_normalize_recovery
BEFORE INSERT OR UPDATE OF recovery_of_group_id, root_transaction_group_id
ON transaction_groups
FOR EACH ROW EXECUTE FUNCTION normalize_transaction_group_recovery();

-- Committed groups must be balanced and account snapshots must be current.
CREATE FUNCTION assert_committed_transaction_balanced()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    target_group_id uuid;
    target_classroom_id uuid;
    target_status varchar(40);
    entry_count bigint;
    amount_sum numeric;
    mismatch_count bigint;
BEGIN
    IF TG_TABLE_NAME = 'transaction_groups' THEN
        target_group_id := coalesce(NEW.id, OLD.id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    ELSE
        target_group_id := coalesce(NEW.transaction_group_id, OLD.transaction_group_id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    END IF;

    SELECT status INTO target_status
      FROM transaction_groups
     WHERE classroom_id = target_classroom_id
       AND id = target_group_id;

    IF target_status IS DISTINCT FROM 'COMMITTED' THEN
        RETURN NULL;
    END IF;

    SELECT count(*), coalesce(sum(amount), 0)
      INTO entry_count, amount_sum
      FROM ledger_entries
     WHERE classroom_id = target_classroom_id
       AND transaction_group_id = target_group_id;

    IF entry_count < 2 OR amount_sum <> 0 THEN
        RAISE EXCEPTION 'committed transaction % is unbalanced: count %, sum %',
            target_group_id, entry_count, amount_sum;
    END IF;

    SELECT count(*)
      INTO mismatch_count
      FROM ledger_entries entry
      JOIN ledger_accounts account
        ON account.classroom_id = entry.classroom_id
       AND account.id = entry.ledger_account_id
     WHERE entry.classroom_id = target_classroom_id
       AND entry.transaction_group_id = target_group_id
       AND (
           entry.balance_before + entry.amount <> entry.balance_after
           OR entry.account_sequence <> account.next_entry_sequence - 1
           OR entry.balance_after <> account.current_balance
       );

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'committed transaction % has stale account sequence or balance',
            target_group_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_committed_balance
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_committed_transaction_balanced();

CREATE CONSTRAINT TRIGGER trg_transaction_groups_committed_balance
AFTER INSERT OR UPDATE OF status ON transaction_groups
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_committed_transaction_balanced();

-- Generic append-only protection.
CREATE FUNCTION reject_row_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable after insertion', TG_TABLE_NAME;
END;
$$;

CREATE FUNCTION reject_mutation_when_published()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    old_json jsonb;
    new_json jsonb;
    old_status text;
    new_status text;
BEGIN
    old_json := to_jsonb(OLD);
    IF (old_json ->> TG_ARGV[0]) IS NULL THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% published rows cannot be deleted', TG_TABLE_NAME;
    END IF;

    new_json := to_jsonb(NEW);
    IF (new_json - ARRAY['updated_at', 'retired_at', 'superseded_at', 'status'])
       IS DISTINCT FROM
       (old_json - ARRAY['updated_at', 'retired_at', 'superseded_at', 'status']) THEN
        RAISE EXCEPTION '% published business fields are immutable', TG_TABLE_NAME;
    END IF;

    IF old_json ? 'status' THEN
        old_status := old_json ->> 'status';
        new_status := new_json ->> 'status';
        IF new_status IS DISTINCT FROM old_status
           AND new_status NOT IN ('RETIRED', 'SUPERSEDED') THEN
            RAISE EXCEPTION '% published status can only retire or supersede', TG_TABLE_NAME;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_committed_transaction_group()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'COMMITTED' THEN
        RAISE EXCEPTION 'committed transaction groups are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transaction_groups_immutable
BEFORE UPDATE OR DELETE ON transaction_groups
FOR EACH ROW EXECUTE FUNCTION protect_committed_transaction_group();
CREATE TRIGGER trg_ledger_entries_immutable
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_policy_snapshots_immutable
BEFORE UPDATE OR DELETE ON policy_snapshots
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_policy_snapshot_sources_immutable
BEFORE UPDATE OR DELETE ON policy_snapshot_sources
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_state_transitions_immutable
BEFORE UPDATE OR DELETE ON state_transitions
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_audit_events_immutable
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_credit_events_immutable
BEFORE UPDATE OR DELETE ON credit_events
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_vote_ballots_immutable
BEFORE UPDATE OR DELETE ON vote_ballots
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_quiz_session_questions_immutable
BEFORE UPDATE OR DELETE ON quiz_session_questions
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_ai_artifacts_immutable
BEFORE UPDATE OR DELETE ON ai_artifacts
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();
CREATE TRIGGER trg_report_snapshots_immutable
BEFORE UPDATE OR DELETE ON report_snapshots
FOR EACH ROW EXECUTE FUNCTION reject_row_mutation();

CREATE TRIGGER trg_course_template_versions_published
BEFORE UPDATE OR DELETE ON course_template_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('published_at');
CREATE TRIGGER trg_store_product_versions_published
BEFORE UPDATE OR DELETE ON store_product_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('published_at');
CREATE TRIGGER trg_financial_product_versions_published
BEFORE UPDATE OR DELETE ON financial_product_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('published_at');
CREATE TRIGGER trg_investment_scenario_versions_published
BEFORE UPDATE OR DELETE ON investment_scenario_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('published_at');
CREATE TRIGGER trg_quiz_question_versions_published
BEFORE UPDATE OR DELETE ON quiz_question_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('published_at');
CREATE TRIGGER trg_quiz_result_versions_published
BEFORE UPDATE OR DELETE ON quiz_result_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('published_at');
CREATE TRIGGER trg_classroom_policy_versions_effective
BEFORE UPDATE OR DELETE ON classroom_policy_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('effective_from');
CREATE TRIGGER trg_loan_policy_versions_effective
BEFORE UPDATE OR DELETE ON loan_policy_versions
FOR EACH ROW EXECUTE FUNCTION reject_mutation_when_published('effective_from');

CREATE FUNCTION protect_published_quiz_student_result()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM quiz_result_versions result_version
         WHERE result_version.classroom_id = OLD.classroom_id
           AND result_version.id = OLD.quiz_result_version_id
           AND result_version.published_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'published quiz student results are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_quiz_student_results_published
BEFORE UPDATE OR DELETE ON quiz_student_results
FOR EACH ROW EXECUTE FUNCTION protect_published_quiz_student_result();

-- Outbox business payload is immutable; only delivery metadata can change.
CREATE FUNCTION protect_outbox_event()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'outbox events cannot be deleted';
    END IF;
    IF NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.classroom_id IS DISTINCT FROM OLD.classroom_id
       OR NEW.realtime_stream_id IS DISTINCT FROM OLD.realtime_stream_id
       OR NEW.channel_sequence IS DISTINCT FROM OLD.channel_sequence
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.aggregate_type IS DISTINCT FROM OLD.aggregate_type
       OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
       OR NEW.state_version IS DISTINCT FROM OLD.state_version
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.occurred_at IS DISTINCT FROM OLD.occurred_at THEN
        RAISE EXCEPTION 'outbox identity and payload are immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_outbox_events_payload_immutable
BEFORE UPDATE OR DELETE ON outbox_events
FOR EACH ROW EXECUTE FUNCTION protect_outbox_event();

-- Published quiz questions must have at least one correct option.
CREATE FUNCTION assert_published_quiz_has_correct_option()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    target_version_id uuid;
    target_classroom_id uuid;
    target_status varchar(40);
BEGIN
    IF TG_TABLE_NAME = 'quiz_question_versions' THEN
        target_version_id := coalesce(NEW.id, OLD.id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    ELSE
        target_version_id := coalesce(NEW.quiz_question_version_id, OLD.quiz_question_version_id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    END IF;
    SELECT status INTO target_status
      FROM quiz_question_versions
     WHERE classroom_id = target_classroom_id AND id = target_version_id;
    IF target_status = 'PUBLISHED'
       AND NOT EXISTS (
           SELECT 1 FROM quiz_options
            WHERE classroom_id = target_classroom_id
              AND quiz_question_version_id = target_version_id
              AND is_correct
       ) THEN
        RAISE EXCEPTION 'published quiz question must have a correct option';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_quiz_options_correct_answer
AFTER INSERT OR UPDATE OR DELETE ON quiz_options
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_published_quiz_has_correct_option();
CREATE CONSTRAINT TRIGGER trg_quiz_question_versions_correct_answer
AFTER INSERT OR UPDATE OF status ON quiz_question_versions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_published_quiz_has_correct_option();

-- An inventory item can be locked by at most one active trade and must be owned
-- by the leg's sender while locked.
CREATE FUNCTION assert_inventory_single_active_trade()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    target_classroom_id uuid;
BEGIN
    target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);

    IF EXISTS (
        SELECT 1
          FROM trade_item_legs leg
          JOIN trades trade
            ON trade.classroom_id = leg.classroom_id
           AND trade.id = leg.trade_id
         WHERE leg.classroom_id = target_classroom_id
           AND trade.status IN ('AWAITING_COUNTERPART', 'AWAITING_TEACHER', 'SETTLING')
         GROUP BY leg.inventory_item_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'inventory item is referenced by multiple active trades';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM trade_item_legs leg
          JOIN trades trade
            ON trade.classroom_id = leg.classroom_id
           AND trade.id = leg.trade_id
          JOIN inventory_items item
            ON item.classroom_id = leg.classroom_id
           AND item.id = leg.inventory_item_id
         WHERE leg.classroom_id = target_classroom_id
           AND trade.status IN ('AWAITING_COUNTERPART', 'AWAITING_TEACHER', 'SETTLING')
           AND (
               item.status <> 'LOCKED_TRADE'
               OR item.locked_trade_id <> trade.id
               OR item.classroom_student_id <> leg.from_student_id
           )
    ) THEN
        RAISE EXCEPTION 'active trade item lock or ownership is inconsistent';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_trade_item_legs_single_active_trade
AFTER INSERT OR UPDATE OR DELETE ON trade_item_legs
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_inventory_single_active_trade();
CREATE CONSTRAINT TRIGGER trg_trades_item_lock_consistency
AFTER INSERT OR UPDATE OF status ON trades
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_inventory_single_active_trade();

-- Shared service guard for commands that create new operating data.
CREATE FUNCTION classroom_accepts_new_operations(target_classroom_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM classrooms
         WHERE id = target_classroom_id
           AND status = 'ACTIVE'
    );
$$;
