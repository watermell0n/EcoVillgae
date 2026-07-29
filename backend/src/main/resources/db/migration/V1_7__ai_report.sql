-- AI runs and teacher approval, asynchronous operations, exports, and reports.

CREATE TABLE async_operations (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    operation_type varchar(40) NOT NULL,
    requested_by_user_id uuid,
    progress_percent integer NOT NULL DEFAULT 0,
    result_location varchar(512),
    error_code varchar(80),
    error_detail text,
    started_at timestamptz,
    completed_at timestamptz,
    expires_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_async_operations_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_async_operations_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_async_operations_requester FOREIGN KEY (requested_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ai_runs (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    async_operation_id uuid,
    requested_by_user_id uuid NOT NULL,
    run_type varchar(40) NOT NULL,
    model_provider varchar(40) NOT NULL,
    model_id varchar(120) NOT NULL,
    prompt_version varchar(40) NOT NULL,
    input_scope jsonb NOT NULL,
    validation_result jsonb,
    requested_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    error_code varchar(80),
    retention_until timestamptz NOT NULL,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_runs_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_ai_runs_async_operation UNIQUE (async_operation_id),
    CONSTRAINT fk_ai_runs_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_runs_async_operation
        FOREIGN KEY (classroom_id, async_operation_id)
        REFERENCES async_operations (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_runs_requester FOREIGN KEY (requested_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ai_artifacts (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    ai_run_id uuid NOT NULL,
    artifact_type varchar(40) NOT NULL,
    content jsonb NOT NULL,
    schema_version varchar(20) NOT NULL,
    safety_status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_artifacts_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_ai_artifacts_run
        FOREIGN KEY (classroom_id, ai_run_id)
        REFERENCES ai_runs (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE ai_proposals (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    ai_run_id uuid NOT NULL,
    proposal_type varchar(40) NOT NULL,
    proposed_values jsonb NOT NULL,
    allowed_ranges jsonb NOT NULL,
    expires_at timestamptz NOT NULL,
    applied_policy_version_id uuid,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_proposals_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_ai_proposals_run
        FOREIGN KEY (classroom_id, ai_run_id)
        REFERENCES ai_runs (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposals_policy_version
        FOREIGN KEY (classroom_id, applied_policy_version_id)
        REFERENCES classroom_policy_versions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE ai_decisions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    ai_proposal_id uuid NOT NULL,
    decided_by_user_id uuid NOT NULL,
    decision varchar(40) NOT NULL,
    reason text,
    decided_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_decisions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_ai_decisions_proposal UNIQUE (ai_proposal_id),
    CONSTRAINT fk_ai_decisions_proposal
        FOREIGN KEY (classroom_id, ai_proposal_id)
        REFERENCES ai_proposals (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_decisions_decider FOREIGN KEY (decided_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE economic_event_drafts (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    ai_run_id uuid,
    activity_session_id uuid,
    title varchar(160) NOT NULL,
    content text NOT NULL,
    economic_effect jsonb NOT NULL,
    valid_from timestamptz,
    valid_until timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_economic_event_drafts_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_economic_event_drafts_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_economic_event_drafts_ai_run
        FOREIGN KEY (classroom_id, ai_run_id)
        REFERENCES ai_runs (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_economic_event_drafts_activity
        FOREIGN KEY (classroom_id, activity_session_id)
        REFERENCES activity_sessions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE export_operations (
    async_operation_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    requested_by_user_id uuid NOT NULL,
    export_type varchar(40) NOT NULL,
    filter_snapshot jsonb NOT NULL,
    result_s3_key varchar(512),
    retention_until timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_export_operations_scope UNIQUE (classroom_id, async_operation_id),
    CONSTRAINT fk_export_operations_async_operation
        FOREIGN KEY (classroom_id, async_operation_id)
        REFERENCES async_operations (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_export_operations_requester FOREIGN KEY (requested_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE report_snapshots (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid,
    report_type varchar(40) NOT NULL,
    schema_version varchar(20) NOT NULL,
    snapshot jsonb NOT NULL,
    as_of timestamptz NOT NULL,
    retention_until timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_report_snapshots_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_report_snapshots_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_snapshots_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

ALTER TABLE quiz_question_versions
    ADD CONSTRAINT fk_quiz_question_versions_ai_run
    FOREIGN KEY (classroom_id, ai_run_id)
    REFERENCES ai_runs (classroom_id, id) ON DELETE RESTRICT;

ALTER TABLE investment_markets
    ADD CONSTRAINT fk_investment_markets_active_scenario
    FOREIGN KEY (classroom_id, active_scenario_version_id)
    REFERENCES investment_scenario_versions (classroom_id, id) ON DELETE RESTRICT;

ALTER TABLE payslips
    ADD CONSTRAINT fk_payslips_policy_snapshot
    FOREIGN KEY (classroom_id, policy_snapshot_id)
    REFERENCES policy_snapshots (classroom_id, id) ON DELETE RESTRICT;

ALTER TABLE payslips
    ADD CONSTRAINT fk_payslips_transaction_group
    FOREIGN KEY (classroom_id, transaction_group_id)
    REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT;
