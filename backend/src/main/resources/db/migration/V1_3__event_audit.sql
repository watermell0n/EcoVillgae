-- Realtime event ordering, transactional outbox, and audit history.

CREATE TABLE realtime_streams (
    id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    stream_type varchar(40) NOT NULL,
    resource_id uuid,
    next_sequence bigint NOT NULL DEFAULT 1,
    retention_from_sequence bigint NOT NULL DEFAULT 1,
    status varchar(40) NOT NULL,
    state_version bigint NOT NULL DEFAULT 0,
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_realtime_streams_scope UNIQUE (classroom_id, id),
    CONSTRAINT fk_realtime_streams_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
);

CREATE TABLE outbox_events (
    event_id uuid PRIMARY KEY,
    classroom_id uuid NOT NULL,
    realtime_stream_id uuid NOT NULL,
    channel_sequence bigint NOT NULL,
    event_type varchar(120) NOT NULL,
    aggregate_type varchar(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    state_version bigint NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    publish_attempts integer NOT NULL DEFAULT 0,
    last_error text,
    CONSTRAINT uq_outbox_events_scope UNIQUE (classroom_id, event_id),
    CONSTRAINT uq_outbox_events_sequence UNIQUE (realtime_stream_id, channel_sequence),
    CONSTRAINT fk_outbox_events_stream
        FOREIGN KEY (classroom_id, realtime_stream_id)
        REFERENCES realtime_streams (classroom_id, id) ON DELETE RESTRICT
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    classroom_id uuid,
    transaction_group_id uuid,
    actor_type varchar(40) NOT NULL,
    actor_id uuid,
    action varchar(120) NOT NULL,
    target_type varchar(40) NOT NULL,
    target_id uuid,
    request_id varchar(128),
    correlation_id varchar(128),
    before_summary jsonb,
    after_summary jsonb,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    retention_until timestamptz NOT NULL,
    CONSTRAINT fk_audit_events_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_events_group
        FOREIGN KEY (classroom_id, transaction_group_id)
        REFERENCES transaction_groups (classroom_id, id) ON DELETE RESTRICT
);
