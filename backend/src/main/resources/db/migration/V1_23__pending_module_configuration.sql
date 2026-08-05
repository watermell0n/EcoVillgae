-- A selected module that still needs teacher policy input is neither disabled
-- (not selected) nor active (available to students).  Keep it visible to the
-- teacher and include it in classroom setup prechecks.
ALTER TABLE classroom_modules
    DROP CONSTRAINT ck_classroom_modules_status,
    ADD CONSTRAINT ck_classroom_modules_status
    CHECK (status IN ('DISABLED', 'PENDING_CONFIGURATION', 'ACTIVE', 'STOPPING'));
