-- 선생님 수업 모듈이 자료 한 장이 아니라 회차 전체를 순서대로 넘길 수 있게 한다.
-- activity_sessions.presentation_image_url·presentation_title은 지우지 않고 현재 장의
-- 사본으로 계속 유지한다. 학생 앱과 GET 활동 조회가 이미 그 두 칼럼을 읽고 있어서,
-- 사본을 두면 넘기기 기능을 붙여도 학생 쪽을 고치지 않아도 된다.

CREATE TABLE activity_presentation_slides (
    id uuid PRIMARY KEY,
    activity_session_id uuid NOT NULL REFERENCES activity_sessions (id) ON DELETE CASCADE,
    sequence_number integer NOT NULL,
    title varchar(120) NOT NULL,
    image_url text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_activity_presentation_slides_sequence
        UNIQUE (activity_session_id, sequence_number),
    CONSTRAINT ck_activity_presentation_slides_sequence
        CHECK (sequence_number >= 1)
);

CREATE INDEX ix_activity_presentation_slides_activity
    ON activity_presentation_slides (activity_session_id, sequence_number);

-- 현재 몇 번째 장을 보여 주는지. 자료가 한 장뿐인 기존 수업은 NULL로 남으며
-- 그 경우 presentation_image_url을 그대로 쓴다.
ALTER TABLE activity_sessions
    ADD COLUMN presentation_cursor integer;

ALTER TABLE activity_sessions
    ADD CONSTRAINT ck_activity_sessions_presentation_cursor
    CHECK (presentation_cursor IS NULL OR presentation_cursor >= 1);
