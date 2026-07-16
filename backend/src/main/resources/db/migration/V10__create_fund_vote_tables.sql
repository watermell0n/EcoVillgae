CREATE TABLE fund_cost_options (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    name VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    UNIQUE (room_id, display_order),
    UNIQUE (room_id, name)
);

CREATE TABLE fund_expenses (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    cost_option_id UUID NOT NULL REFERENCES fund_cost_options(id),
    name_snapshot VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    transaction_id UUID NOT NULL UNIQUE REFERENCES financial_transactions(id),
    applied_by UUID NOT NULL REFERENCES users(id),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id)
);

CREATE TABLE vote_sessions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    question VARCHAR(200) NOT NULL,
    fund_balance_snapshot BIGINT NOT NULL CHECK (fund_balance_snapshot >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('READY', 'OPEN', 'CLOSED', 'DECIDED')),
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    decided_option_id UUID,
    decided_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id)
);

CREATE TABLE vote_options (
    id UUID PRIMARY KEY,
    vote_session_id UUID NOT NULL REFERENCES vote_sessions(id),
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    required_amount BIGINT NOT NULL CHECK (required_amount >= 0),
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    UNIQUE (vote_session_id, display_order)
);

ALTER TABLE vote_sessions
    ADD CONSTRAINT fk_vote_sessions_decided_option
    FOREIGN KEY (decided_option_id) REFERENCES vote_options(id);

CREATE TABLE votes (
    id UUID PRIMARY KEY,
    vote_session_id UUID NOT NULL REFERENCES vote_sessions(id),
    student_id UUID NOT NULL REFERENCES students(id),
    option_id UUID NOT NULL REFERENCES vote_options(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (vote_session_id, student_id)
);

CREATE INDEX idx_fund_cost_options_room ON fund_cost_options(room_id, display_order);
CREATE INDEX idx_votes_session_option ON votes(vote_session_id, option_id);

