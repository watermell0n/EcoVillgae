-- Persist the teacher-approved public projection allowlist for guided activities.

ALTER TABLE activity_sessions
    ADD COLUMN public_fields jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE activity_sessions
    ADD CONSTRAINT ck_activity_sessions_public_fields
    CHECK (jsonb_typeof(public_fields) = 'array');
