ALTER TABLE auth_sessions
    DROP CONSTRAINT IF EXISTS auth_sessions_principal_type_check;

ALTER TABLE auth_sessions
    DROP CONSTRAINT IF EXISTS auth_sessions_check;

ALTER TABLE auth_sessions
    ADD CONSTRAINT chk_auth_sessions_principal_type
        CHECK (principal_type IN ('TEACHER', 'STUDENT', 'ADMIN'));

ALTER TABLE auth_sessions
    ADD CONSTRAINT chk_auth_sessions_principal_reference
        CHECK (
            (principal_type IN ('TEACHER', 'ADMIN')
                AND user_id IS NOT NULL
                AND student_id IS NULL
                AND login_version IS NULL)
            OR
            (principal_type = 'STUDENT'
                AND user_id IS NULL
                AND student_id IS NOT NULL
                AND login_version IS NOT NULL)
        );

CREATE TABLE student_presences (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    student_id UUID NOT NULL UNIQUE REFERENCES students(id) ON DELETE CASCADE,
    reported_stage VARCHAR(50) NOT NULL,
    current_module VARCHAR(40),
    current_page VARCHAR(80) NOT NULL,
    client_sync_version BIGINT NOT NULL DEFAULT 0 CHECK (client_sync_version >= 0),
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('VISIBLE', 'HIDDEN')),
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    reconnected_at TIMESTAMPTZ,
    forced_offline_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_student_presences_room
    ON student_presences(room_id, last_seen_at DESC);

CREATE TABLE activity_events (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    student_id UUID REFERENCES students(id) ON DELETE SET NULL,
    actor_type VARCHAR(20) NOT NULL CHECK (
        actor_type IN ('STUDENT', 'TEACHER', 'ADMIN', 'SYSTEM')
    ),
    actor_id UUID,
    event_type VARCHAR(60) NOT NULL,
    stage VARCHAR(50),
    module VARCHAR(40),
    page VARCHAR(80),
    request_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activity_events_room_time
    ON activity_events(room_id, occurred_at DESC);

CREATE INDEX idx_activity_events_student_time
    ON activity_events(student_id, occurred_at DESC);

CREATE INDEX idx_activity_events_type_time
    ON activity_events(event_type, occurred_at DESC);
