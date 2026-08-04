package com.b201.ecovillage.classroom.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "state_transitions")
public class ModuleStateTransition {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "previous_status", updatable = false, length = 40)
    private String previousStatus;

    @Column(name = "next_status", nullable = false, updatable = false, length = 40)
    private String nextStatus;

    @Column(nullable = false, updatable = false, length = 80)
    private String command;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 40)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "state_version", nullable = false, updatable = false)
    private long stateVersion;

    @Column(name = "reason_code", updatable = false, length = 80)
    private String reasonCode;

    @Column(columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "transaction_group_id", updatable = false)
    private UUID transactionGroupId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModuleStateTransition() {
    }

    private ModuleStateTransition(
        UUID id,
        UUID classroomId,
        UUID moduleId,
        String previousStatus,
        String nextStatus,
        UUID actorId,
        long stateVersion,
        String reasonCode,
        String reason,
        Instant occurredAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.aggregateType = "CLASSROOM_MODULE";
        this.aggregateId = Objects.requireNonNull(moduleId, "moduleId must not be null");
        this.previousStatus = previousStatus;
        this.nextStatus = required(nextStatus, "nextStatus");
        this.command = "STOP_MODULE";
        this.actorType = "TEACHER";
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.stateVersion = stateVersion;
        this.reasonCode = reasonCode;
        this.reason = required(reason, "reason");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.createdAt = occurredAt;
    }

    public static ModuleStateTransition stopped(
        UUID id,
        UUID classroomId,
        UUID moduleId,
        UUID actorId,
        long stateVersion,
        String reasonCode,
        String reason,
        Instant occurredAt
    ) {
        return new ModuleStateTransition(
            id,
            classroomId,
            moduleId,
            "ACTIVE",
            "STOPPING",
            actorId,
            stateVersion,
            reasonCode,
            reason,
            occurredAt
        );
    }

    public UUID getId() {
        return id;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
