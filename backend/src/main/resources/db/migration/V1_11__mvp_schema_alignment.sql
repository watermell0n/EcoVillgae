-- MVP API/physical schema alignment. Existing migrations remain immutable.

ALTER TABLE classrooms
    ADD COLUMN planned_student_count integer;

UPDATE classrooms AS classroom
   SET planned_student_count = GREATEST(
       1,
       (
           SELECT count(*)::integer
             FROM classroom_students AS student
            WHERE student.classroom_id = classroom.id
              AND student.retired_at IS NULL
       )
   );

ALTER TABLE classrooms
    ALTER COLUMN planned_student_count SET NOT NULL,
    ADD CONSTRAINT ck_classrooms_planned_student_count
        CHECK (planned_student_count > 0);

ALTER TABLE salary_roles
    ADD COLUMN role_code varchar(40);

UPDATE salary_roles
   SET role_code = 'ROLE_' || upper(substr(replace(id::text, '-', ''), 1, 16));

ALTER TABLE salary_roles
    ALTER COLUMN role_code SET NOT NULL,
    ADD CONSTRAINT ck_salary_roles_role_code
        CHECK (role_code ~ '^[A-Z][A-Z0-9_]{0,39}$');

CREATE UNIQUE INDEX ux_salary_roles_role_code
    ON salary_roles (classroom_id, role_code);

CREATE TABLE autonomous_schedule_modules (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    autonomous_schedule_id uuid NOT NULL,
    module_code varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_autonomous_schedule_modules_scope
        UNIQUE (classroom_id, id),
    CONSTRAINT uq_autonomous_schedule_modules_module
        UNIQUE (autonomous_schedule_id, module_code),
    CONSTRAINT fk_autonomous_schedule_modules_schedule
        FOREIGN KEY (classroom_id, autonomous_schedule_id)
        REFERENCES autonomous_schedules (classroom_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_autonomous_schedule_modules_classroom_module
        FOREIGN KEY (classroom_id, module_code)
        REFERENCES classroom_modules (classroom_id, module_code) ON DELETE RESTRICT
);

CREATE INDEX ix_autonomous_schedule_modules_module
    ON autonomous_schedule_modules (classroom_id, module_code, autonomous_schedule_id);

CREATE TABLE school_ai_setting_versions (
    id uuid PRIMARY KEY,
    school_id uuid NOT NULL,
    version integer NOT NULL,
    enabled boolean NOT NULL,
    reason text NOT NULL,
    effective_from timestamptz NOT NULL,
    superseded_at timestamptz,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_school_ai_setting_versions_version
        UNIQUE (school_id, version),
    CONSTRAINT fk_school_ai_setting_versions_school
        FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_ai_setting_versions_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_school_ai_setting_versions_validity
        CHECK (
            version > 0
            AND btrim(reason) <> ''
            AND (superseded_at IS NULL OR superseded_at > effective_from)
        )
);

CREATE UNIQUE INDEX ux_school_ai_setting_versions_current
    ON school_ai_setting_versions (school_id)
    WHERE superseded_at IS NULL;
