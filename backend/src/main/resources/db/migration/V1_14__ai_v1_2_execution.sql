-- AI schema v1.2 execution metadata, acknowledgement, and monitor deduplication.

ALTER TABLE ai_runs
    ALTER COLUMN requested_by_user_id DROP NOT NULL,
    ALTER COLUMN model_provider DROP NOT NULL,
    ALTER COLUMN model_id DROP NOT NULL,
    ALTER COLUMN prompt_version DROP NOT NULL;

ALTER TABLE ai_runs
    ADD COLUMN trigger_type varchar(40),
    ADD COLUMN analysis_period_from timestamptz,
    ADD COLUMN analysis_period_to timestamptz;

UPDATE ai_runs
   SET trigger_type = CASE
       WHEN run_type = 'AUTO_MONITOR' THEN 'AUTO_MONITOR'
       ELSE 'TEACHER_REQUEST'
   END
 WHERE trigger_type IS NULL;

ALTER TABLE ai_runs
    ALTER COLUMN trigger_type SET NOT NULL;

ALTER TABLE ai_runs DROP CONSTRAINT ck_ai_runs_status;
ALTER TABLE ai_runs ADD CONSTRAINT ck_ai_runs_status
    CHECK (status IN (
        'REQUESTED', 'DATA_CHECK', 'INSUFFICIENT_DATA', 'GENERATING', 'VALIDATING',
        'AWAITING_TEACHER', 'ACKNOWLEDGED', 'APPROVED', 'REJECTED', 'EXPIRED',
        'APPLYING', 'COMPLETED', 'TIMED_OUT', 'FAILED', 'REJECTED_OUTPUT',
        'APPLY_FAILED'
    ));

ALTER TABLE ai_proposals
    ADD COLUMN source_type varchar(40),
    ADD COLUMN severity varchar(20),
    ADD COLUMN metric_code varchar(80),
    ADD COLUMN direction varchar(20),
    ADD COLUMN dedupe_key varchar(200);

UPDATE ai_proposals
   SET source_type = 'LEGACY'
 WHERE source_type IS NULL;

ALTER TABLE ai_proposals
    ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE ai_proposals DROP CONSTRAINT ck_ai_proposals_status;
ALTER TABLE ai_proposals ADD CONSTRAINT ck_ai_proposals_status
    CHECK (status IN (
        'AWAITING_TEACHER', 'ACKNOWLEDGED', 'APPROVED', 'REJECTED',
        'EXPIRED', 'APPLIED', 'APPLY_FAILED'
    ));

CREATE UNIQUE INDEX uq_ai_proposals_monitor_dedupe
    ON ai_proposals (classroom_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

ALTER TABLE ai_decisions DROP CONSTRAINT uq_ai_decisions_proposal;

CREATE UNIQUE INDEX uq_ai_decisions_final
    ON ai_decisions (ai_proposal_id)
    WHERE decision IN ('APPROVE', 'REJECT');

CREATE UNIQUE INDEX uq_ai_decisions_acknowledge
    ON ai_decisions (ai_proposal_id)
    WHERE decision = 'ACKNOWLEDGE';

ALTER TABLE economic_event_drafts
    ADD COLUMN source_type varchar(40),
    ADD COLUMN source_proposal_id uuid,
    ADD COLUMN investment_price_step_id uuid,
    ADD COLUMN revealed_at timestamptz;

UPDATE economic_event_drafts
   SET source_type = 'TEACHER_REQUEST'
 WHERE source_type IS NULL;

ALTER TABLE economic_event_drafts
    ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE economic_event_drafts
    ADD CONSTRAINT fk_economic_event_drafts_source_proposal
    FOREIGN KEY (classroom_id, source_proposal_id)
    REFERENCES ai_proposals (classroom_id, id) ON DELETE RESTRICT;
