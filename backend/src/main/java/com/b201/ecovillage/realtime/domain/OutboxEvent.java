package com.b201.ecovillage.realtime.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "realtime_stream_id", nullable = false, updatable = false)
    private UUID realtimeStreamId;

    @Column(name = "channel_sequence", nullable = false, updatable = false)
    private long channelSequence;

    @Column(name = "event_type", nullable = false, updatable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "state_version", nullable = false, updatable = false)
    private long stateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEvent() {
    }

    private OutboxEvent(
        UUID eventId,
        UUID classroomId,
        UUID realtimeStreamId,
        long channelSequence,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        long stateVersion,
        Map<String, Object> payload,
        Instant occurredAt
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.realtimeStreamId = Objects.requireNonNull(
            realtimeStreamId,
            "realtimeStreamId must not be null"
        );
        if (channelSequence < 1 || stateVersion < 0) {
            throw new IllegalArgumentException("event sequence and state version are invalid");
        }
        this.channelSequence = channelSequence;
        this.eventType = requiredText(eventType, "eventType");
        this.aggregateType = requiredText(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        this.stateVersion = stateVersion;
        this.payload = new LinkedHashMap<>(
            Objects.requireNonNull(payload, "payload must not be null")
        );
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.publishAttempts = 0;
    }

    public static OutboxEvent pending(
        UUID eventId,
        UUID classroomId,
        UUID realtimeStreamId,
        long channelSequence,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        long stateVersion,
        Map<String, Object> payload,
        Instant occurredAt
    ) {
        return new OutboxEvent(
            eventId,
            classroomId,
            realtimeStreamId,
            channelSequence,
            eventType,
            aggregateType,
            aggregateId,
            stateVersion,
            payload,
            occurredAt
        );
    }

    public UUID getEventId() {
        return eventId;
    }

    public Map<String, Object> getPayload() {
        return Map.copyOf(payload);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
