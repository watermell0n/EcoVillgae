CREATE TABLE room_command_requests (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    idempotency_key VARCHAR(100) NOT NULL,
    command VARCHAR(40) NOT NULL,
    target_stage VARCHAR(50) NOT NULL,
    expected_version INTEGER NOT NULL,
    resulting_version INTEGER NOT NULL,
    resulting_sync_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, idempotency_key)
);

CREATE INDEX idx_room_command_requests_room_id
    ON room_command_requests(room_id);
