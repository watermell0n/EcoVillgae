-- Global classroom identity, immutable classroom currency display name,
-- and the opt-in GAME top-three public projection.

ALTER TABLE classrooms
    ADD COLUMN currency_name varchar(10);

-- Existing development rows predate the required creation field. New creation
-- requests must always provide the value and the table has no lasting default.
UPDATE classrooms
   SET currency_name = '에코'
 WHERE currency_name IS NULL;

ALTER TABLE classrooms
    ALTER COLUMN currency_name SET NOT NULL,
    ALTER COLUMN classroom_code TYPE varchar(8);

ALTER TABLE classrooms
    DROP CONSTRAINT uq_classrooms_school_code;

ALTER TABLE classrooms
    ADD CONSTRAINT uq_classrooms_classroom_code UNIQUE (classroom_code),
    ADD CONSTRAINT ck_classrooms_code_format
        CHECK (classroom_code ~ '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$'),
    ADD CONSTRAINT ck_classrooms_currency_name
        CHECK (
            char_length(currency_name) BETWEEN 1 AND 10
            AND currency_name ~ '^[가-힣A-Za-z0-9]+$'
        );

CREATE FUNCTION prevent_classroom_identity_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.classroom_code IS DISTINCT FROM OLD.classroom_code THEN
        RAISE EXCEPTION 'classroom_code is immutable';
    END IF;
    IF NEW.currency_name IS DISTINCT FROM OLD.currency_name THEN
        RAISE EXCEPTION 'currency_name is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_classrooms_immutable_identity
BEFORE UPDATE OF classroom_code, currency_name ON classrooms
FOR EACH ROW EXECUTE FUNCTION prevent_classroom_identity_update();

DROP VIEW public_quiz_leaderboard;

CREATE VIEW public_quiz_leaderboard AS
SELECT
    result.classroom_id,
    result.quiz_session_id,
    student_result.rank,
    student.nickname,
    equipped.character_code,
    equipped.character_name
FROM quiz_result_versions result
JOIN quiz_sessions session
  ON session.classroom_id = result.classroom_id
 AND session.activity_session_id = result.quiz_session_id
JOIN quiz_student_results student_result
  ON student_result.classroom_id = result.classroom_id
 AND student_result.quiz_result_version_id = result.id
JOIN classroom_students student
  ON student.classroom_id = student_result.classroom_id
 AND student.id = student_result.classroom_student_id
LEFT JOIN LATERAL (
    SELECT
        catalog.character_code,
        catalog.name AS character_name
    FROM inventory_items item
    JOIN character_catalog catalog
      ON catalog.id = item.character_catalog_id
    WHERE item.classroom_id = student.classroom_id
      AND item.classroom_student_id = student.id
      AND item.status = 'EQUIPPED'
    ORDER BY item.equipped_at DESC NULLS LAST, item.id
    LIMIT 1
) equipped ON true
WHERE session.mode = 'GAME'
  AND session.leaderboard_public = true
  AND result.published_at IS NOT NULL
  AND student_result.rank BETWEEN 1 AND 3;
