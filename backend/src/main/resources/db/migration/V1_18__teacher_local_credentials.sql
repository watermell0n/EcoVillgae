CREATE TABLE teacher_credentials (
    teacher_user_id uuid PRIMARY KEY,
    email varchar(254) NOT NULL,
    password_hash text NOT NULL,
    credential_version bigint NOT NULL DEFAULT 1,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_teacher_credentials_email UNIQUE (email),
    CONSTRAINT fk_teacher_credentials_user FOREIGN KEY (teacher_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);
