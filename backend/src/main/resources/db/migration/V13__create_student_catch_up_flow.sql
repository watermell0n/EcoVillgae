CREATE TABLE student_flow_states (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    student_id UUID NOT NULL REFERENCES students(id),
    flow_mode VARCHAR(20) NOT NULL CHECK (
        flow_mode IN ('LIVE', 'CATCH_UP', 'AWAITING_JOIN', 'LOCKED')
    ),
    effective_stage VARCHAR(50) NOT NULL,
    join_status VARCHAR(30) NOT NULL CHECK (
        join_status IN ('NOT_REQUIRED', 'PENDING', 'APPROVED_PENDING_BOUNDARY', 'JOINED', 'LOCKED')
    ),
    reward_rate_bps INTEGER NOT NULL DEFAULT 10000 CHECK (
        reward_rate_bps BETWEEN 0 AND 10000
    ),
    version INTEGER NOT NULL DEFAULT 0,
    catch_up_started_at TIMESTAMPTZ,
    catch_up_completed_at TIMESTAMPTZ,
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (student_id)
);

CREATE INDEX idx_student_flow_states_room_mode
    ON student_flow_states(room_id, flow_mode);

CREATE TABLE student_activity_progress (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    student_id UUID NOT NULL REFERENCES students(id),
    activity_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (
        status IN ('AVAILABLE', 'IN_PROGRESS', 'COMPLETED', 'MISSED', 'WAIVED', 'RESULT_ONLY')
    ),
    attempt_type VARCHAR(20) NOT NULL CHECK (
        attempt_type IN ('LIVE', 'LATE', 'RECOVERY')
    ),
    reward_rate_bps INTEGER NOT NULL DEFAULT 10000 CHECK (
        reward_rate_bps BETWEEN 0 AND 10000
    ),
    override_reason VARCHAR(200),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (student_id, activity_code)
);

CREATE INDEX idx_student_activity_progress_room_student
    ON student_activity_progress(room_id, student_id);

ALTER TABLE settlement_items
    ADD COLUMN attempt_type VARCHAR(20) NOT NULL DEFAULT 'LIVE',
    ADD COLUMN reward_rate_bps INTEGER NOT NULL DEFAULT 10000;

ALTER TABLE settlement_items
    ADD CONSTRAINT chk_settlement_items_attempt_type
        CHECK (attempt_type IN ('LIVE', 'LATE', 'RECOVERY')),
    ADD CONSTRAINT chk_settlement_items_reward_rate
        CHECK (reward_rate_bps BETWEEN 0 AND 10000);

ALTER TABLE financial_settlement_items
    ADD COLUMN attempt_type VARCHAR(20) NOT NULL DEFAULT 'LIVE',
    ADD COLUMN reward_rate_bps INTEGER NOT NULL DEFAULT 10000;

ALTER TABLE financial_settlement_items
    ADD CONSTRAINT chk_financial_items_attempt_type
        CHECK (attempt_type IN ('LIVE', 'LATE', 'RECOVERY')),
    ADD CONSTRAINT chk_financial_items_reward_rate
        CHECK (reward_rate_bps BETWEEN 0 AND 10000);

CREATE TABLE vote_eligible_students (
    vote_session_id UUID NOT NULL REFERENCES vote_sessions(id),
    student_id UUID NOT NULL REFERENCES students(id),
    eligible_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vote_session_id, student_id)
);

CREATE INDEX idx_vote_eligible_students_student
    ON vote_eligible_students(student_id);
