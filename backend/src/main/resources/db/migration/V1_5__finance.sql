-- Deposits, savings, investment simulation, educational loans, and credit.

CREATE TABLE financial_products (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    product_type varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    description text,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_financial_products_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_financial_products_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE financial_product_versions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    financial_product_id uuid NOT NULL,
    version integer NOT NULL,
    product_type varchar(40) NOT NULL,
    term_units integer NOT NULL,
    interest_rate_bps integer NOT NULL,
    early_termination_rate_bps integer NOT NULL,
    installment_count integer,
    installment_amount bigint,
    minimum_amount bigint NOT NULL,
    maximum_amount bigint NOT NULL,
    status varchar(40) NOT NULL,
    published_at timestamptz NOT NULL,
    retired_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_financial_product_versions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_financial_product_versions_version UNIQUE (financial_product_id, version),
    CONSTRAINT fk_financial_product_versions_product
        FOREIGN KEY (classroom_id, financial_product_id)
        REFERENCES financial_products (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE deposit_accounts (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    product_version_id uuid NOT NULL,
    principal_amount bigint NOT NULL,
    expected_interest_amount bigint NOT NULL,
    opened_at timestamptz NOT NULL DEFAULT now(),
    matures_at timestamptz NOT NULL,
    closed_at timestamptz,
    opening_transaction_group_id uuid NOT NULL,
    closing_transaction_group_id uuid,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_deposit_accounts_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_deposit_accounts_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_deposit_accounts_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_deposit_accounts_product
        FOREIGN KEY (classroom_id, product_version_id)
        REFERENCES financial_product_versions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_deposit_accounts_opening_group
        FOREIGN KEY (classroom_id, opening_transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_deposit_accounts_closing_group
        FOREIGN KEY (classroom_id, closing_transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE savings_accounts (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    product_version_id uuid NOT NULL,
    installment_amount bigint NOT NULL,
    installment_count integer NOT NULL,
    paid_installment_count integer NOT NULL DEFAULT 0,
    principal_balance bigint NOT NULL DEFAULT 0,
    expected_interest_amount bigint NOT NULL,
    opened_at timestamptz NOT NULL DEFAULT now(),
    matures_at timestamptz NOT NULL,
    closed_at timestamptz,
    opening_transaction_group_id uuid,
    closing_transaction_group_id uuid,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_savings_accounts_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_savings_accounts_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_savings_accounts_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_savings_accounts_product
        FOREIGN KEY (classroom_id, product_version_id)
        REFERENCES financial_product_versions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_savings_accounts_opening_group
        FOREIGN KEY (classroom_id, opening_transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_savings_accounts_closing_group
        FOREIGN KEY (classroom_id, closing_transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE savings_installments (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    savings_account_id uuid NOT NULL,
    installment_number integer NOT NULL,
    scheduled_amount bigint NOT NULL,
    due_at timestamptz NOT NULL,
    retryable_until timestamptz NOT NULL,
    paid_at timestamptz,
    transaction_group_id uuid,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_savings_installments_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_savings_installments_number UNIQUE (savings_account_id, installment_number),
    CONSTRAINT fk_savings_installments_account
        FOREIGN KEY (classroom_id, savings_account_id)
        REFERENCES savings_accounts (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_savings_installments_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE investment_markets (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    active_scenario_version_id uuid,
    current_step integer NOT NULL DEFAULT 0,
    opened_at timestamptz,
    closed_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investment_markets_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_investment_markets_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE investment_scenario_versions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    investment_market_id uuid NOT NULL,
    version integer NOT NULL,
    name varchar(120) NOT NULL,
    scenario_seed varchar(128),
    status varchar(40) NOT NULL,
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investment_scenario_versions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_investment_scenario_versions_version UNIQUE (investment_market_id, version),
    CONSTRAINT fk_investment_scenario_versions_market
        FOREIGN KEY (classroom_id, investment_market_id)
        REFERENCES investment_markets (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE investment_assets (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    scenario_version_id uuid NOT NULL,
    symbol varchar(20) NOT NULL,
    name varchar(120) NOT NULL,
    display_order integer NOT NULL DEFAULT 0,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investment_assets_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_investment_assets_symbol UNIQUE (scenario_version_id, symbol),
    CONSTRAINT fk_investment_assets_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_assets_scenario
        FOREIGN KEY (classroom_id, scenario_version_id)
        REFERENCES investment_scenario_versions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE investment_price_steps (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    investment_asset_id uuid NOT NULL,
    step_number integer NOT NULL,
    price bigint NOT NULL,
    change_rate_bps integer NOT NULL,
    revealed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investment_price_steps_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_investment_price_steps_step UNIQUE (investment_asset_id, step_number),
    CONSTRAINT fk_investment_price_steps_asset
        FOREIGN KEY (classroom_id, investment_asset_id)
        REFERENCES investment_assets (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE investment_orders (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    investment_asset_id uuid NOT NULL,
    side varchar(10) NOT NULL,
    quantity integer NOT NULL,
    executed_price bigint,
    executed_price_step integer,
    transaction_group_id uuid,
    submitted_at timestamptz NOT NULL DEFAULT now(),
    executed_at timestamptz,
    reject_reason varchar(80),
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investment_orders_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_investment_orders_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_orders_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_orders_asset
        FOREIGN KEY (classroom_id, investment_asset_id)
        REFERENCES investment_assets (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_orders_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE investment_positions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    investment_asset_id uuid NOT NULL,
    quantity integer NOT NULL DEFAULT 0,
    average_price bigint NOT NULL DEFAULT 0,
    state_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investment_positions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_investment_positions_student_asset
        UNIQUE (classroom_student_id, investment_asset_id),
    CONSTRAINT fk_investment_positions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_positions_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_positions_asset
        FOREIGN KEY (classroom_id, investment_asset_id)
        REFERENCES investment_assets (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE loan_policy_versions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    version integer NOT NULL,
    maximum_principal bigint NOT NULL,
    interest_rate_bps integer NOT NULL,
    term_units integer NOT NULL,
    installment_count integer NOT NULL,
    grace_units integer NOT NULL DEFAULT 0,
    credit_adjustments jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(40) NOT NULL,
    effective_from timestamptz NOT NULL,
    superseded_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_loan_policy_versions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_loan_policy_versions_version UNIQUE (classroom_id, version),
    CONSTRAINT fk_loan_policy_versions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE loan_applications (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    loan_policy_version_id uuid NOT NULL,
    requested_amount bigint NOT NULL,
    approved_amount bigint,
    credit_level_snapshot varchar(40) NOT NULL,
    credit_score_snapshot integer NOT NULL,
    decision_reason text,
    decided_by_user_id uuid,
    decided_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_loan_applications_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_loan_applications_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_applications_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_applications_policy
        FOREIGN KEY (classroom_id, loan_policy_version_id)
        REFERENCES loan_policy_versions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_applications_decider FOREIGN KEY (decided_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE loans (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    loan_application_id uuid NOT NULL,
    principal_amount bigint NOT NULL,
    applied_interest_rate_bps integer NOT NULL,
    credit_level_at_approval varchar(40) NOT NULL,
    outstanding_principal bigint NOT NULL,
    outstanding_interest bigint NOT NULL DEFAULT 0,
    disbursement_transaction_group_id uuid NOT NULL,
    started_at timestamptz NOT NULL,
    matures_at timestamptz NOT NULL,
    closed_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_loans_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_loans_application UNIQUE (loan_application_id),
    CONSTRAINT fk_loans_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loans_application
        FOREIGN KEY (classroom_id, loan_application_id)
        REFERENCES loan_applications (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_loans_disbursement_group
        FOREIGN KEY (classroom_id, disbursement_transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE loan_installments (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    loan_id uuid NOT NULL,
    installment_number integer NOT NULL,
    scheduled_principal bigint NOT NULL,
    scheduled_interest bigint NOT NULL,
    due_at timestamptz NOT NULL,
    grace_until timestamptz NOT NULL,
    paid_principal bigint NOT NULL DEFAULT 0,
    paid_interest bigint NOT NULL DEFAULT 0,
    paid_at timestamptz,
    transaction_group_id uuid,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_loan_installments_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_loan_installments_number UNIQUE (loan_id, installment_number),
    CONSTRAINT fk_loan_installments_loan
        FOREIGN KEY (classroom_id, loan_id)
        REFERENCES loans (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_installments_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE credit_profiles (
    classroom_student_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    level varchar(40) NOT NULL,
    score integer NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    evaluated_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_profiles_scope UNIQUE (classroom_id, classroom_student_id),
    CONSTRAINT fk_credit_profiles_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE credit_events (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    event_type varchar(40) NOT NULL,
    score_delta integer NOT NULL,
    score_before integer NOT NULL,
    score_after integer NOT NULL,
    level_before varchar(40) NOT NULL,
    level_after varchar(40) NOT NULL,
    reason text NOT NULL,
    related_type varchar(40),
    related_id uuid,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_events_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_credit_events_profile
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES credit_profiles (classroom_id, classroom_student_id) ON DELETE RESTRICT
);
