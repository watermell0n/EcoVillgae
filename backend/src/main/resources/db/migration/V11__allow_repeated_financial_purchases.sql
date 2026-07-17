ALTER TABLE deposits
    DROP CONSTRAINT IF EXISTS deposits_session_id_student_id_key;

ALTER TABLE investment_orders
    DROP CONSTRAINT IF EXISTS investment_orders_session_id_student_id_key;

CREATE UNIQUE INDEX uq_deposits_open_draft
    ON deposits(session_id, student_id)
    WHERE status = 'DRAFT';

CREATE UNIQUE INDEX uq_investment_orders_open_draft
    ON investment_orders(session_id, student_id)
    WHERE status = 'DRAFT';

CREATE INDEX idx_deposits_session_student_status
    ON deposits(session_id, student_id, status);

CREATE INDEX idx_investment_orders_session_student_status
    ON investment_orders(session_id, student_id, status);
