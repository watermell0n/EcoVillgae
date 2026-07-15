ALTER TABLE auth_sessions
    ALTER COLUMN token_hash TYPE VARCHAR(64);
