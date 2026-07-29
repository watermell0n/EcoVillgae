-- Teacher-led activities, quizzes, regrading, and anonymous public-fund votes.

CREATE TABLE activity_sessions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    activity_type varchar(40) NOT NULL,
    policy_snapshot_id uuid,
    created_by_user_id uuid NOT NULL,
    public_display_enabled boolean NOT NULL DEFAULT false,
    starts_at timestamptz,
    ends_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_activity_sessions_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_activity_sessions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_sessions_policy_snapshot
        FOREIGN KEY (classroom_id, policy_snapshot_id)
        REFERENCES policy_snapshots (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_sessions_creator FOREIGN KEY (created_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE activity_participants (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    activity_session_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    participant_status varchar(40) NOT NULL,
    joined_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_activity_participants_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_activity_participants_student UNIQUE (activity_session_id, classroom_student_id),
    CONSTRAINT fk_activity_participants_session
        FOREIGN KEY (classroom_id, activity_session_id)
        REFERENCES activity_sessions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_participants_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_questions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    content_type varchar(40) NOT NULL,
    title varchar(160),
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_questions_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_quiz_questions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_questions_creator FOREIGN KEY (created_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE quiz_question_versions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_question_id uuid NOT NULL,
    version integer NOT NULL,
    content_type varchar(40) NOT NULL,
    question_type varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    prompt text NOT NULL,
    explanation text,
    source varchar(40) NOT NULL,
    ai_run_id uuid,
    published_at timestamptz,
    retired_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_question_versions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_quiz_question_versions_version UNIQUE (quiz_question_id, version),
    CONSTRAINT fk_quiz_question_versions_question
        FOREIGN KEY (classroom_id, quiz_question_id)
        REFERENCES quiz_questions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_options (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_question_version_id uuid NOT NULL,
    option_key varchar(20) NOT NULL,
    option_text text NOT NULL,
    is_correct boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_options_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_quiz_options_key UNIQUE (quiz_question_version_id, option_key),
    CONSTRAINT fk_quiz_options_question_version
        FOREIGN KEY (classroom_id, quiz_question_version_id)
        REFERENCES quiz_question_versions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_sessions (
    activity_session_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    mode varchar(20) NOT NULL,
    content_type varchar(40) NOT NULL,
    reward_policy_public jsonb NOT NULL DEFAULT '{}'::jsonb,
    leaderboard_public boolean NOT NULL DEFAULT false,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_sessions_scope UNIQUE (classroom_id, activity_session_id),
    CONSTRAINT fk_quiz_sessions_activity
        FOREIGN KEY (classroom_id, activity_session_id)
        REFERENCES activity_sessions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_session_questions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    quiz_question_version_id uuid NOT NULL,
    sequence_number integer NOT NULL,
    prompt_snapshot text NOT NULL,
    options_snapshot jsonb NOT NULL,
    correct_answer_snapshot jsonb NOT NULL,
    score_value integer NOT NULL,
    time_limit_seconds integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_session_questions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_quiz_session_questions_sequence UNIQUE (quiz_session_id, sequence_number),
    CONSTRAINT fk_quiz_session_questions_session
        FOREIGN KEY (classroom_id, quiz_session_id)
        REFERENCES quiz_sessions (classroom_id, activity_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_session_questions_question_version
        FOREIGN KEY (classroom_id, quiz_question_version_id)
        REFERENCES quiz_question_versions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_submissions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    mode varchar(20) NOT NULL,
    answer_sheet_version integer NOT NULL,
    submitted_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_submissions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_quiz_submissions_student_version
        UNIQUE (quiz_session_id, classroom_student_id, answer_sheet_version),
    CONSTRAINT fk_quiz_submissions_session
        FOREIGN KEY (classroom_id, quiz_session_id)
        REFERENCES quiz_sessions (classroom_id, activity_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_submissions_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_answers (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_submission_id uuid NOT NULL,
    quiz_session_question_id uuid NOT NULL,
    selected_option_key varchar(20),
    free_text text,
    is_correct boolean,
    score_awarded integer,
    response_time_ms integer,
    answered_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_answers_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_quiz_answers_question UNIQUE (quiz_submission_id, quiz_session_question_id),
    CONSTRAINT fk_quiz_answers_submission
        FOREIGN KEY (classroom_id, quiz_submission_id)
        REFERENCES quiz_submissions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_answers_session_question
        FOREIGN KEY (classroom_id, quiz_session_question_id)
        REFERENCES quiz_session_questions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_result_versions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    version integer NOT NULL,
    status varchar(40) NOT NULL,
    reward_transaction_group_id uuid,
    published_at timestamptz,
    supersedes_result_version_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_result_versions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_quiz_result_versions_version UNIQUE (quiz_session_id, version),
    CONSTRAINT fk_quiz_result_versions_session
        FOREIGN KEY (classroom_id, quiz_session_id)
        REFERENCES quiz_sessions (classroom_id, activity_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_result_versions_reward_group
        FOREIGN KEY (classroom_id, reward_transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_result_versions_supersedes
        FOREIGN KEY (classroom_id, supersedes_result_version_id)
        REFERENCES quiz_result_versions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_student_results (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_result_version_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    score integer NOT NULL,
    correct_count integer NOT NULL,
    rank integer,
    reward_amount bigint NOT NULL DEFAULT 0,
    total_response_time_ms bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_student_results_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_quiz_student_results_student
        UNIQUE (quiz_result_version_id, classroom_student_id),
    CONSTRAINT fk_quiz_student_results_result_version
        FOREIGN KEY (classroom_id, quiz_result_version_id)
        REFERENCES quiz_result_versions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_student_results_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE quiz_question_issues (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_session_question_id uuid NOT NULL,
    reported_by_student_id uuid,
    reason text NOT NULL,
    resolution text,
    resolved_by_user_id uuid,
    resolved_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_question_issues_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_quiz_question_issues_session_question
        FOREIGN KEY (classroom_id, quiz_session_question_id)
        REFERENCES quiz_session_questions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_question_issues_reporter
        FOREIGN KEY (classroom_id, reported_by_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_question_issues_resolver FOREIGN KEY (resolved_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE regrading_runs (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    source_result_version_id uuid NOT NULL,
    new_result_version_id uuid,
    reason text NOT NULL,
    started_by_user_id uuid NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_regrading_runs_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_regrading_runs_session
        FOREIGN KEY (classroom_id, quiz_session_id)
        REFERENCES quiz_sessions (classroom_id, activity_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_regrading_runs_source_result
        FOREIGN KEY (classroom_id, source_result_version_id)
        REFERENCES quiz_result_versions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_regrading_runs_new_result
        FOREIGN KEY (classroom_id, new_result_version_id)
        REFERENCES quiz_result_versions (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_regrading_runs_starter FOREIGN KEY (started_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE votes (
    activity_session_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    title varchar(160) NOT NULL,
    description text,
    anonymous boolean NOT NULL DEFAULT true,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    opens_at timestamptz,
    closes_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_votes_scope UNIQUE (classroom_id, activity_session_id),
    CONSTRAINT fk_votes_activity
        FOREIGN KEY (classroom_id, activity_session_id)
        REFERENCES activity_sessions (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE vote_options (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    vote_id uuid NOT NULL,
    label varchar(160) NOT NULL,
    description text,
    sort_order integer NOT NULL,
    budget_amount bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vote_options_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_vote_options_sort_order UNIQUE (vote_id, sort_order),
    CONSTRAINT fk_vote_options_vote
        FOREIGN KEY (classroom_id, vote_id)
        REFERENCES votes (classroom_id, activity_session_id) ON DELETE RESTRICT
);

CREATE TABLE vote_eligibilities (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    vote_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    eligibility_token_hash varchar(128) NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vote_eligibilities_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_vote_eligibilities_student UNIQUE (vote_id, classroom_student_id),
    CONSTRAINT uq_vote_eligibilities_token UNIQUE (vote_id, eligibility_token_hash),
    CONSTRAINT fk_vote_eligibilities_vote
        FOREIGN KEY (classroom_id, vote_id)
        REFERENCES votes (classroom_id, activity_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vote_eligibilities_student
        FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE vote_ballots (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    vote_id uuid NOT NULL,
    vote_option_id uuid NOT NULL,
    eligibility_token_hash varchar(128) NOT NULL,
    receipt_hash varchar(128) NOT NULL,
    cast_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vote_ballots_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_vote_ballots_token UNIQUE (vote_id, eligibility_token_hash),
    CONSTRAINT uq_vote_ballots_receipt UNIQUE (receipt_hash),
    CONSTRAINT fk_vote_ballots_vote
        FOREIGN KEY (classroom_id, vote_id)
        REFERENCES votes (classroom_id, activity_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vote_ballots_option
        FOREIGN KEY (classroom_id, vote_option_id)
        REFERENCES vote_options (classroom_id, id) ON DELETE RESTRICT
);
