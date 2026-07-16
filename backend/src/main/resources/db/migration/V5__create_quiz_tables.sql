CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    day_number SMALLINT NOT NULL CHECK (day_number IN (1, 2)),
    title VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('READY', 'OPEN', 'CLOSED', 'SETTLED')),
    question_count INTEGER NOT NULL CHECK (question_count > 0),
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, day_number)
);

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    order_no INTEGER NOT NULL CHECK (order_no > 0),
    question_type VARCHAR(30) NOT NULL CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE')),
    prompt TEXT NOT NULL,
    options JSONB NOT NULL,
    correct_answer VARCHAR(50) NOT NULL,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (quiz_id, order_no)
);

CREATE TABLE quiz_submissions (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    student_id UUID NOT NULL REFERENCES students(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'SUBMITTED')),
    correct_count INTEGER NOT NULL DEFAULT 0 CHECK (correct_count >= 0),
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (quiz_id, student_id)
);

CREATE TABLE quiz_answers (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES quiz_submissions(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES quiz_questions(id),
    answer VARCHAR(50) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (submission_id, question_id)
);

CREATE INDEX idx_quizzes_room_day ON quizzes(room_id, day_number);
CREATE INDEX idx_quiz_questions_quiz ON quiz_questions(quiz_id, order_no);
CREATE INDEX idx_quiz_submissions_quiz ON quiz_submissions(quiz_id);
CREATE INDEX idx_quiz_submissions_student ON quiz_submissions(student_id);
CREATE INDEX idx_quiz_answers_submission ON quiz_answers(submission_id);
