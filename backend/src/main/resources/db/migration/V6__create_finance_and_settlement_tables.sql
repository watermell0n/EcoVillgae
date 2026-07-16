CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    student_id UUID REFERENCES students(id),
    account_type VARCHAR(40) NOT NULL CHECK (
        account_type IN (
            'STUDENT_WALLET', 'STUDENT_DEPOSIT', 'STUDENT_INVESTMENT',
            'CLASS_FUND', 'SHOP_REVENUE', 'CLASS_EXPENSE', 'SYSTEM_TREASURY'
        )
    ),
    balance BIGINT NOT NULL DEFAULT 0,
    allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounts_owner CHECK (
        (account_type LIKE 'STUDENT_%' AND student_id IS NOT NULL)
        OR (account_type NOT LIKE 'STUDENT_%' AND student_id IS NULL)
    ),
    CONSTRAINT chk_accounts_balance CHECK (allow_negative OR balance >= 0),
    UNIQUE NULLS NOT DISTINCT (room_id, student_id, account_type)
);

CREATE TABLE settlement_runs (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    session_id UUID NOT NULL REFERENCES sessions(id),
    started_by UUID NOT NULL REFERENCES users(id),
    settlement_type VARCHAR(40) NOT NULL CHECK (
        settlement_type IN ('DAY1_INCOME', 'DAY2_FINANCE', 'DAY2_QUIZ_REWARD', 'INFLATION')
    ),
    status VARCHAR(20) NOT NULL CHECK (
        status IN ('PREVIEWED', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    target_count INTEGER NOT NULL DEFAULT 0 CHECK (target_count >= 0),
    success_count INTEGER NOT NULL DEFAULT 0 CHECK (success_count >= 0),
    failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    UNIQUE (room_id, settlement_type)
);

CREATE TABLE settlement_attempts (
    id UUID PRIMARY KEY,
    settlement_run_id UUID NOT NULL REFERENCES settlement_runs(id),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    replayed BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE settlement_items (
    id UUID PRIMARY KEY,
    settlement_run_id UUID NOT NULL REFERENCES settlement_runs(id),
    student_id UUID NOT NULL REFERENCES students(id),
    quiz_submitted BOOLEAN NOT NULL,
    correct_count INTEGER NOT NULL DEFAULT 0 CHECK (correct_count >= 0),
    role_income BIGINT NOT NULL CHECK (role_income >= 0),
    participation_reward BIGINT NOT NULL CHECK (participation_reward >= 0),
    correct_reward BIGINT NOT NULL CHECK (correct_reward >= 0),
    gross_income BIGINT NOT NULL CHECK (gross_income >= 0),
    income_tax BIGINT NOT NULL CHECK (income_tax >= 0),
    net_income BIGINT NOT NULL CHECK (net_income >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (settlement_run_id, student_id)
);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    session_id UUID REFERENCES sessions(id),
    student_id UUID REFERENCES students(id),
    actor_id UUID,
    settlement_run_id UUID REFERENCES settlement_runs(id),
    transaction_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'REJECTED', 'FAILED')),
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('STUDENT', 'TEACHER', 'ADMIN', 'SYSTEM')),
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    reason_code VARCHAR(80),
    description VARCHAR(255) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE TABLE financial_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES financial_transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount BIGINT NOT NULL CHECK (amount <> 0),
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    entry_order INTEGER NOT NULL CHECK (entry_order > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (transaction_id, entry_order)
);

CREATE INDEX idx_accounts_room_student ON accounts(room_id, student_id);
CREATE INDEX idx_settlement_runs_room ON settlement_runs(room_id);
CREATE INDEX idx_settlement_items_student ON settlement_items(student_id);
CREATE INDEX idx_financial_transactions_room_student
    ON financial_transactions(room_id, student_id, created_at);
CREATE INDEX idx_financial_entries_account ON financial_entries(account_id, created_at);
