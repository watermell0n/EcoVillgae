-- Identity, course templates, classrooms, authentication, policy, and payroll.

CREATE TABLE schools (
    id uuid PRIMARY KEY,
    school_code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_schools_school_code UNIQUE (school_code)
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    external_subject varchar(128) NOT NULL,
    display_name varchar(120) NOT NULL,
    user_type varchar(40) NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_external_subject UNIQUE (external_subject)
);

CREATE TABLE school_memberships (
    id uuid PRIMARY KEY,
    school_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role varchar(40) NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    assigned_at timestamptz NOT NULL DEFAULT now(),
    ended_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_school_memberships_school FOREIGN KEY (school_id)
        REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_memberships_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE course_templates (
    id uuid PRIMARY KEY,
    template_code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    grade_min smallint NOT NULL,
    grade_max smallint NOT NULL,
    scale_label varchar(20) NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_course_templates_template_code UNIQUE (template_code)
);

CREATE TABLE course_template_versions (
    id uuid PRIMARY KEY,
    course_template_id uuid NOT NULL,
    version integer NOT NULL,
    difficulty_profile jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(40) NOT NULL,
    published_at timestamptz,
    retired_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_template_versions_template FOREIGN KEY (course_template_id)
        REFERENCES course_templates (id) ON DELETE RESTRICT,
    CONSTRAINT uq_course_template_versions_version UNIQUE (course_template_id, version)
);

CREATE TABLE course_template_modules (
    id uuid PRIMARY KEY,
    course_template_version_id uuid NOT NULL,
    module_code varchar(40) NOT NULL,
    recommendation_level varchar(40) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_template_modules_version FOREIGN KEY (course_template_version_id)
        REFERENCES course_template_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_course_template_modules_code UNIQUE (course_template_version_id, module_code)
);

CREATE TABLE classrooms (
    id uuid PRIMARY KEY,
    school_id uuid NOT NULL,
    course_template_version_id uuid,
    classroom_code varchar(40) NOT NULL,
    grade smallint NOT NULL,
    scale_label varchar(20) NOT NULL,
    actual_starts_on date,
    actual_ends_on date,
    app_mode_override varchar(40),
    time_zone varchar(60) NOT NULL DEFAULT 'Asia/Seoul',
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_classrooms_school FOREIGN KEY (school_id)
        REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT fk_classrooms_course_version FOREIGN KEY (course_template_version_id)
        REFERENCES course_template_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_classrooms_school_code UNIQUE (school_id, classroom_code)
);

CREATE TABLE classroom_teachers (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role varchar(40) NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    ended_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_classroom_teachers_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_classroom_teachers_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_classroom_teachers_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE salary_roles (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    name varchar(80) NOT NULL,
    gross_amount bigint NOT NULL,
    deductions_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    sort_order integer NOT NULL DEFAULT 0,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_salary_roles_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_salary_roles_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE classroom_students (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    salary_role_id uuid,
    roster_number integer NOT NULL,
    nickname varchar(40) NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT now(),
    retired_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_classroom_students_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_classroom_students_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_classroom_students_salary_role FOREIGN KEY (classroom_id, salary_role_id)
        REFERENCES salary_roles (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE student_credentials (
    classroom_student_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    password_hash text NOT NULL,
    credential_version bigint NOT NULL DEFAULT 1,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_credentials_scope UNIQUE (classroom_id, classroom_student_id),
    CONSTRAINT fk_student_credentials_student FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE device_links (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    device_fingerprint_hash varchar(128) NOT NULL,
    device_name varchar(120),
    linked_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz,
    revoked_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_device_links_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_device_links_fingerprint UNIQUE (classroom_student_id, device_fingerprint_hash),
    CONSTRAINT fk_device_links_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_links_student FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE student_sessions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    device_link_id uuid,
    token_hash varchar(128) NOT NULL,
    status varchar(40) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_sessions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_student_sessions_token UNIQUE (token_hash),
    CONSTRAINT fk_student_sessions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_sessions_student FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_sessions_device FOREIGN KEY (classroom_id, device_link_id)
        REFERENCES device_links (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE classroom_modules (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    module_code varchar(40) NOT NULL,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    enabled_at timestamptz,
    stopped_at timestamptz,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_classroom_modules_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_classroom_modules_code UNIQUE (classroom_id, module_code),
    CONSTRAINT fk_classroom_modules_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE classroom_policy_versions (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    policy_area varchar(40) NOT NULL,
    version integer NOT NULL,
    values jsonb NOT NULL,
    schema_version varchar(20) NOT NULL,
    source varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    effective_from timestamptz NOT NULL,
    superseded_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_classroom_policy_versions_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_classroom_policy_versions_version UNIQUE (classroom_id, policy_area, version),
    CONSTRAINT fk_classroom_policy_versions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE autonomous_schedules (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    day_of_week smallint NOT NULL,
    local_start_time time NOT NULL,
    local_end_time time NOT NULL,
    time_zone varchar(60) NOT NULL DEFAULT 'Asia/Seoul',
    enabled boolean NOT NULL DEFAULT true,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_autonomous_schedules_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_autonomous_schedules_time UNIQUE (classroom_id, day_of_week, local_start_time),
    CONSTRAINT fk_autonomous_schedules_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE payslips (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    salary_role_id uuid,
    period_key varchar(40) NOT NULL,
    gross_amount bigint NOT NULL,
    deduction_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    net_amount bigint NOT NULL,
    policy_snapshot_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_payslips_scope UNIQUE (classroom_id, id),
    CONSTRAINT uq_payslips_student_period UNIQUE (classroom_student_id, period_key),
    CONSTRAINT fk_payslips_student FOREIGN KEY (classroom_id, classroom_student_id)
        REFERENCES classroom_students (classroom_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_payslips_salary_role FOREIGN KEY (classroom_id, salary_role_id)
        REFERENCES salary_roles (classroom_id, id) ON DELETE RESTRICT
);
