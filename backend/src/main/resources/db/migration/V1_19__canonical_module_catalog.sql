-- V1_12 may already be applied, so keep that historical migration immutable and
-- canonicalize both its recommendation rows and any classroom data here.

CREATE FUNCTION pg_temp.canonical_module_code(input_code text)
RETURNS text
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT CASE upper(trim(input_code))
        WHEN 'INCOME' THEN 'ROLE_SALARY'
        WHEN 'SALARY' THEN 'ROLE_SALARY'
        WHEN 'TAX' THEN 'INCOME_TAX'
        ELSE upper(trim(input_code))
    END
$$;

-- Published course recommendations: remove the technical wallet row, convert
-- legacy aliases, and rewrite every prerequisite to canonical output codes.
UPDATE course_template_modules AS canonical
   SET config_json = legacy.config_json || canonical.config_json,
       sort_order = least(canonical.sort_order, legacy.sort_order),
       updated_at = greatest(canonical.updated_at, legacy.updated_at)
  FROM course_template_modules AS legacy
 WHERE legacy.course_template_version_id = canonical.course_template_version_id
   AND legacy.module_code IN ('INCOME', 'SALARY', 'TAX')
   AND canonical.module_code = pg_temp.canonical_module_code(legacy.module_code);

DELETE FROM course_template_modules AS legacy
 USING course_template_modules AS canonical
 WHERE legacy.course_template_version_id = canonical.course_template_version_id
   AND legacy.id <> canonical.id
   AND legacy.module_code IN ('INCOME', 'SALARY', 'TAX')
   AND canonical.module_code = pg_temp.canonical_module_code(legacy.module_code);

UPDATE course_template_modules
   SET module_code = pg_temp.canonical_module_code(module_code),
       updated_at = now()
 WHERE module_code IN ('INCOME', 'SALARY', 'TAX');

DELETE FROM course_template_modules
 WHERE module_code = 'WALLET';

UPDATE course_template_modules AS course_module
   SET config_json = jsonb_set(
       course_module.config_json,
       '{prerequisites}',
       coalesce((
           SELECT jsonb_agg(to_jsonb(mapped.module_code) ORDER BY mapped.first_position)
             FROM (
                 SELECT pg_temp.canonical_module_code(raw.value) AS module_code,
                        min(raw.ordinality) AS first_position
                   FROM jsonb_array_elements_text(
                       coalesce(
                           course_module.config_json -> 'prerequisites',
                           '[]'::jsonb
                       )
                   ) WITH ORDINALITY AS raw(value, ordinality)
                  WHERE pg_temp.canonical_module_code(raw.value) <> 'WALLET'
                  GROUP BY pg_temp.canonical_module_code(raw.value)
             ) AS mapped
       ), '[]'::jsonb),
       true
   ),
       updated_at = now()
 WHERE course_module.config_json ? 'prerequisites';

-- The schedule link uses the mutable business key (classroom_id, module_code),
-- so suspend that FK while aliases and duplicate rows are merged in place.
ALTER TABLE autonomous_schedule_modules
    DROP CONSTRAINT fk_autonomous_schedule_modules_classroom_module;

DELETE FROM autonomous_schedule_modules
 WHERE module_code = 'WALLET';

WITH ranked_schedule_modules AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY classroom_id,
                            autonomous_schedule_id,
                            pg_temp.canonical_module_code(module_code)
               ORDER BY
                   CASE
                       WHEN module_code = pg_temp.canonical_module_code(module_code) THEN 0
                       ELSE 1
                   END,
                   id
           ) AS duplicate_order
      FROM autonomous_schedule_modules
)
DELETE FROM autonomous_schedule_modules AS duplicate
 USING ranked_schedule_modules AS ranked
 WHERE duplicate.id = ranked.id
   AND ranked.duplicate_order > 1;

UPDATE autonomous_schedule_modules
   SET module_code = pg_temp.canonical_module_code(module_code)
 WHERE module_code IN ('INCOME', 'SALARY', 'TAX');

DELETE FROM classroom_modules
 WHERE module_code = 'WALLET';

CREATE TEMPORARY TABLE canonical_classroom_module_survivors ON COMMIT DROP AS
SELECT classroom_id,
       canonical_code,
       id AS survivor_id
  FROM (
      SELECT module.id,
             module.classroom_id,
             pg_temp.canonical_module_code(module.module_code) AS canonical_code,
             row_number() OVER (
                 PARTITION BY module.classroom_id,
                              pg_temp.canonical_module_code(module.module_code)
                 ORDER BY
                     CASE
                         WHEN module.module_code = pg_temp.canonical_module_code(module.module_code)
                             THEN 0
                         ELSE 1
                     END,
                     CASE module.status
                         WHEN 'ACTIVE' THEN 0
                         WHEN 'STOPPING' THEN 1
                         ELSE 2
                     END,
                     module.updated_at DESC,
                     module.id
             ) AS survivor_order
        FROM classroom_modules AS module
  ) AS ranked
 WHERE survivor_order = 1;

UPDATE classroom_modules AS survivor
   SET config_json = coalesce((
           SELECT jsonb_object_agg(
                      item.key,
                      item.value
                      ORDER BY
                          CASE candidate.status
                              WHEN 'DISABLED' THEN 0
                              WHEN 'STOPPING' THEN 1
                              WHEN 'ACTIVE' THEN 2
                          END,
                          candidate.updated_at
                  )
             FROM classroom_modules AS candidate
             CROSS JOIN LATERAL jsonb_each(candidate.config_json) AS item
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
       ), '{}'::jsonb),
       enabled_at = (
           SELECT min(candidate.enabled_at)
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
       ),
       stopped_at = CASE (
           SELECT candidate.status
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
            ORDER BY CASE candidate.status
                WHEN 'ACTIVE' THEN 0
                WHEN 'STOPPING' THEN 1
                ELSE 2
            END
            LIMIT 1
       ) WHEN 'ACTIVE' THEN null ELSE (
           SELECT max(candidate.stopped_at)
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
       ) END,
       status = (
           SELECT candidate.status
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
            ORDER BY CASE candidate.status
                WHEN 'ACTIVE' THEN 0
                WHEN 'STOPPING' THEN 1
                ELSE 2
            END
            LIMIT 1
       ),
       state_version = (
           SELECT max(candidate.state_version)
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
       ),
       status_changed_at = (
           SELECT max(candidate.status_changed_at)
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
       ),
       created_at = (
           SELECT min(candidate.created_at)
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
       ),
       updated_at = (
           SELECT max(candidate.updated_at)
             FROM classroom_modules AS candidate
            WHERE candidate.classroom_id = mapping.classroom_id
              AND pg_temp.canonical_module_code(candidate.module_code) = mapping.canonical_code
       )
  FROM canonical_classroom_module_survivors AS mapping
 WHERE survivor.id = mapping.survivor_id;

DELETE FROM classroom_modules AS duplicate
 USING canonical_classroom_module_survivors AS mapping
 WHERE duplicate.classroom_id = mapping.classroom_id
   AND pg_temp.canonical_module_code(duplicate.module_code) = mapping.canonical_code
   AND duplicate.id <> mapping.survivor_id;

UPDATE classroom_modules AS survivor
   SET module_code = mapping.canonical_code
  FROM canonical_classroom_module_survivors AS mapping
 WHERE survivor.id = mapping.survivor_id
   AND survivor.module_code <> mapping.canonical_code;

ALTER TABLE autonomous_schedule_modules
    ADD CONSTRAINT fk_autonomous_schedule_modules_classroom_module
        FOREIGN KEY (classroom_id, module_code)
        REFERENCES classroom_modules (classroom_id, module_code) ON DELETE RESTRICT;

-- Enforce the catalog at the database boundary as well as at the API boundary.
ALTER TABLE course_template_modules
    ADD CONSTRAINT ck_course_template_modules_canonical_code CHECK (module_code IN (
        'ROLE_SALARY', 'INCOME_TAX', 'STORE', 'BUDGET', 'PAYSLIP',
        'PUBLIC_FUND', 'PUBLIC_BUDGET', 'DEPOSIT', 'SAVINGS', 'CREDIT',
        'LOAN', 'INVESTMENT', 'RISK_MANAGEMENT', 'MARKET_PRICE', 'TRADE',
        'PHYSICAL_STORE', 'CHARACTER', 'QUIZ_GAME', 'QUIZ_TEST', 'AI_EVENT',
        'CUMULATIVE_REPORT'
    ));

ALTER TABLE classroom_modules
    ADD CONSTRAINT ck_classroom_modules_canonical_code CHECK (module_code IN (
        'ROLE_SALARY', 'INCOME_TAX', 'STORE', 'BUDGET', 'PAYSLIP',
        'PUBLIC_FUND', 'PUBLIC_BUDGET', 'DEPOSIT', 'SAVINGS', 'CREDIT',
        'LOAN', 'INVESTMENT', 'RISK_MANAGEMENT', 'MARKET_PRICE', 'TRADE',
        'PHYSICAL_STORE', 'CHARACTER', 'QUIZ_GAME', 'QUIZ_TEST', 'AI_EVENT',
        'CUMULATIVE_REPORT'
    ));

ALTER TABLE autonomous_schedule_modules
    ADD CONSTRAINT ck_autonomous_schedule_modules_canonical_code CHECK (module_code IN (
        'ROLE_SALARY', 'INCOME_TAX', 'STORE', 'BUDGET', 'PAYSLIP',
        'PUBLIC_FUND', 'PUBLIC_BUDGET', 'DEPOSIT', 'SAVINGS', 'CREDIT',
        'LOAN', 'INVESTMENT', 'RISK_MANAGEMENT', 'MARKET_PRICE', 'TRADE',
        'PHYSICAL_STORE', 'CHARACTER', 'QUIZ_GAME', 'QUIZ_TEST', 'AI_EVENT',
        'CUMULATIVE_REPORT'
    ));
