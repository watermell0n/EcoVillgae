package com.b201.ecovillage.vote.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "votes")
public class Vote {

    @Id
    @Column(name = "activity_session_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean anonymous;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VoteStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vote() {
    }

    private Vote(
        UUID id,
        UUID classroomId,
        String title,
        String description,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.title = requiredText(title, 160, "title");
        this.description = optionalText(description, 2_000, "description");
        this.anonymous = true;
        this.status = VoteStatus.READY;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static Vote ready(
        UUID id,
        UUID classroomId,
        String title,
        String description,
        Instant createdAt
    ) {
        return new Vote(id, classroomId, title, description, createdAt);
    }

    public void open(Instant changedAt) {
        transition(VoteStatus.READY, VoteStatus.OPEN, changedAt);
        opensAt = changedAt;
    }

    public void close(Instant changedAt) {
        transition(VoteStatus.OPEN, VoteStatus.CLOSED, changedAt);
        closesAt = changedAt;
    }

    public void reopen(Instant changedAt) {
        transition(VoteStatus.CLOSED, VoteStatus.OPEN, changedAt);
        opensAt = changedAt;
        closesAt = null;
    }

    public void publishResult(Instant changedAt) {
        transition(VoteStatus.CLOSED, VoteStatus.RESULT_PUBLISHED, changedAt);
    }

    private void transition(
        VoteStatus expected,
        VoteStatus next,
        Instant changedAt
    ) {
        if (status != expected) {
            throw new IllegalStateException(
                "vote cannot transition from " + status + " to " + next
            );
        }
        status = next;
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public VoteStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getOpensAt() {
        return opensAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    private static String requiredText(
        String value,
        int maxLength,
        String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static String optionalText(
        String value,
        int maxLength,
        String field
    ) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
