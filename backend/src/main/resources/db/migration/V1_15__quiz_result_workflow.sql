-- Prevent duplicate student reports while retaining every report and regrade as audit history.

CREATE UNIQUE INDEX uq_quiz_question_issues_student_report
    ON quiz_question_issues (quiz_session_question_id, reported_by_student_id)
    WHERE reported_by_student_id IS NOT NULL;

CREATE UNIQUE INDEX uq_regrading_runs_active_session
    ON regrading_runs (quiz_session_id)
    WHERE status IN ('REQUESTED', 'RUNNING');
