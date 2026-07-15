CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    principal_type VARCHAR(20) NOT NULL CHECK (principal_type IN ('TEACHER', 'STUDENT')),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    student_id UUID REFERENCES students(id) ON DELETE CASCADE,
    login_version INTEGER,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (principal_type = 'TEACHER' AND user_id IS NOT NULL AND student_id IS NULL AND login_version IS NULL)
        OR
        (principal_type = 'STUDENT' AND user_id IS NULL AND student_id IS NOT NULL AND login_version IS NOT NULL)
    )
);

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_student_id ON auth_sessions(student_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions(expires_at);
