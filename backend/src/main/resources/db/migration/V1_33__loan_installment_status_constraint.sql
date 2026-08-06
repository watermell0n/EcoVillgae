ALTER TABLE loan_installments
    ADD CONSTRAINT ck_loan_installments_status
    CHECK (status IN ('SCHEDULED', 'MISSED', 'PAID'));
