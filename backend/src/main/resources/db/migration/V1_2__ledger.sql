-- Double-entry ledger, policy snapshots, idempotency, and state history.

CREATE TABLE ledger_accounts (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    owner_type varchar(40) NOT NULL,
    classroom_student_id uuid,
    system_owner_key varchar(80),
    account_type varchar(40) NOT NULL,
    current_balance bigint NOT NULL DEFAULT 0,
    next_entry_sequence bigint NOT NULL DEFAULT 1,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_accounts_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_ledger_accounts_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_accounts_student FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE policy_snapshots (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    snapshot_hash varchar(128) NOT NULL,
    schema_version varchar(20) NOT NULL,
    calculated_values jsonb NOT NULL,
    rounding_rule varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_policy_snapshots_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_policy_snapshots_hash UNIQUE (classroom_id, snapshot_hash),
    CONSTRAINT fk_policy_snapshots_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE policy_snapshot_sources (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    policy_snapshot_id uuid NOT NULL,
    source_type varchar(40) NOT NULL,
    source_id uuid NOT NULL,
    source_version integer NOT NULL,
    source_hash varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_policy_snapshot_sources_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_policy_snapshot_sources_source
        UNIQUE (policy_snapshot_id, source_type, source_id),
    CONSTRAINT fk_policy_snapshot_sources_snapshot
        FOREIGN KEY (classroom_id, policy_snapshot_id)
        REFERENCES policy_snapshots (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE transaction_groups (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    transaction_type varchar(40) NOT NULL,
    actor_type varchar(40) NOT NULL,
    actor_id uuid,
    policy_snapshot_id uuid,
    recovery_of_group_id uuid,
    root_transaction_group_id uuid,
    request_id varchar(128) NOT NULL,
    correlation_id varchar(128) NOT NULL,
    reason text,
    committed_at timestamptz,
    rejected_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction_groups_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_transaction_groups_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_groups_policy_snapshot
        FOREIGN KEY (classroom_id, policy_snapshot_id)
        REFERENCES policy_snapshots (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_groups_recovery
        FOREIGN KEY (classroom_id, recovery_of_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_groups_root
        FOREIGN KEY (classroom_id, root_transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE ledger_entries (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    ledger_account_id uuid NOT NULL,
    account_sequence bigint NOT NULL,
    amount bigint NOT NULL,
    balance_before bigint NOT NULL,
    balance_after bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_entries_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_ledger_entries_account_sequence UNIQUE (ledger_account_id, account_sequence),
    CONSTRAINT uq_ledger_entries_group_account UNIQUE (transaction_group_id, ledger_account_id),
    CONSTRAINT fk_ledger_entries_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_entries_account
        FOREIGN KEY (classroom_id, ledger_account_id)
        REFERENCES ledger_accounts (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE transaction_business_refs (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    ref_type varchar(40) NOT NULL,
    ref_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction_business_refs_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_transaction_business_refs_ref
        UNIQUE (transaction_group_id, ref_type, ref_id),
    CONSTRAINT fk_transaction_business_refs_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE transaction_inquiries (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    question text NOT NULL,
    resolution text,
    resolved_by_user_id uuid,
    resolved_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction_inquiries_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_transaction_inquiries_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_inquiries_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_inquiries_resolver FOREIGN KEY (resolved_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE idempotency_records (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    route_key varchar(160) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash varchar(128) NOT NULL,
    response_status integer NOT NULL,
    response_body jsonb NOT NULL,
    transaction_group_id uuid,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_records_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_idempotency_records_key
        UNIQUE (classroom_id, actor_id, route_key, idempotency_key),
    CONSTRAINT fk_idempotency_records_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_idempotency_records_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE state_transitions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    aggregate_type varchar(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    previous_status varchar(40),
    next_status varchar(40) NOT NULL,
    command varchar(80) NOT NULL,
    actor_type varchar(40) NOT NULL,
    actor_id uuid,
    state_version bigint NOT NULL,
    reason_code varchar(80),
    reason text,
    transaction_group_id uuid,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_state_transitions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_state_transitions_version
        UNIQUE (classroom_id, aggregate_type, aggregate_id, state_version),
    CONSTRAINT fk_state_transitions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_state_transitions_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);
