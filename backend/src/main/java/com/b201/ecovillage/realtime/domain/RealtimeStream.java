package com.b201.ecovillage.realtime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "realtime_streams")
public class RealtimeStream {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "stream_type", nullable = false, updatable = false, length = 40)
    private String streamType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "next_sequence", nullable = false)
    private long nextSequence;

    @Column(name = "retention_from_sequence", nullable = false)
    private long retentionFromSequence;

    @Column(nullable = false, length = 40)
    private String status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RealtimeStream() {
    }

    private RealtimeStream(
        UUID id,
        UUID classroomId,
        String streamType,
        UUID resourceId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.streamType = requiredText(streamType, "streamType");
        this.resourceId = resourceId;
        this.nextSequence = 1;
        this.retentionFromSequence = 1;
        this.status = "ACTIVE";
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static RealtimeStream active(
        UUID id,
        UUID classroomId,
        String streamType,
        UUID resourceId,
        Instant createdAt
    ) {
        return new RealtimeStream(id, classroomId, streamType, resourceId, createdAt);
    }

    public long reserveNextSequence(Instant changedAt) {
        long reserved = nextSequence;
        nextSequence++;
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        return reserved;
    }

    public UUID getId() {
        return id;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
