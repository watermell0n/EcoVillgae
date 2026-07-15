CREATE TABLE users (
    id UUID PRIMARY KEY,
    role VARCHAR(20) NOT NULL CHECK (role IN ('TEACHER', 'ADMIN')),
    login_id VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    teacher_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    expected_student_count INTEGER NOT NULL CHECK (expected_student_count > 0),
    status VARCHAR(30) NOT NULL CHECK (
        status IN ('PREPARING', 'DAY1_ACTIVE', 'DAY1_CLOSED', 'DAY2_ACTIVE', 'COMPLETED', 'ARCHIVED')
    ),
    current_day SMALLINT NOT NULL DEFAULT 1 CHECK (current_day IN (1, 2)),
    current_stage VARCHAR(50) NOT NULL,
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('GUIDED', 'FREE', 'CLOSED')),
    version INTEGER NOT NULL DEFAULT 0,
    sync_version BIGINT NOT NULL DEFAULT 0,
    scenario_locked_at TIMESTAMPTZ,
    inflation_applied_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rooms_teacher_id ON rooms(teacher_id);
CREATE INDEX idx_rooms_status ON rooms(status);

CREATE TABLE room_rules (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL UNIQUE REFERENCES rooms(id),
    selected_scenario_id UUID,
    role_income BIGINT NOT NULL DEFAULT 100,
    day1_quiz_count INTEGER NOT NULL DEFAULT 6,
    day1_participation_reward BIGINT NOT NULL DEFAULT 10,
    day1_correct_reward BIGINT NOT NULL DEFAULT 10,
    day2_quiz_count INTEGER NOT NULL DEFAULT 5,
    day2_participation_reward BIGINT NOT NULL DEFAULT 10,
    day2_correct_reward BIGINT NOT NULL DEFAULT 5,
    income_tax_bps INTEGER NOT NULL DEFAULT 1000,
    deposit_interest_bps INTEGER NOT NULL DEFAULT 1000,
    financial_tax_bps INTEGER NOT NULL DEFAULT 1000,
    inflation_bps INTEGER NOT NULL DEFAULT 1000,
    deposit_min_amount BIGINT NOT NULL DEFAULT 20,
    deposit_unit BIGINT NOT NULL DEFAULT 10,
    investment_min_amount BIGINT NOT NULL DEFAULT 10,
    investment_unit BIGINT NOT NULL DEFAULT 10,
    rules_locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE students (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    participant_number INTEGER NOT NULL,
    participation_code VARCHAR(20) NOT NULL UNIQUE,
    nickname VARCHAR(50),
    password_hash VARCHAR(255),
    status VARCHAR(20) NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'ABSENT', 'DISABLED')),
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    login_version INTEGER NOT NULL DEFAULT 0,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, participant_number)
);

CREATE INDEX idx_students_room_id ON students(room_id);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    day_number SMALLINT NOT NULL CHECK (day_number IN (1, 2)),
    status VARCHAR(20) NOT NULL CHECK (status IN ('READY', 'ACTIVE', 'CLOSED')),
    current_stage VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, day_number)
);

CREATE TABLE module_states (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions(id),
    current_page_id UUID,
    module_type VARCHAR(40) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('LOCKED', 'GUIDED', 'ACTIVE', 'CLOSED')),
    current_page_order INTEGER,
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, module_type)
);
