package com.b201.ecovillage.audit.domain;

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
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", updatable = false)
    private UUID classroomId;

    @Column(name = "transaction_group_id", updatable = false)
    private UUID transactionGroupId;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 40)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false, length = 120)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(name = "request_id", updatable = false, length = 128)
    private String requestId;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_summary", updatable = false)
    private Map<String, Object> beforeSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_summary", updatable = false)
    private Map<String, Object> afterSummary;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "retention_until", nullable = false, updatable = false)
    private Instant retentionUntil;

    protected AuditEvent() {
    }

    private AuditEvent(
        UUID id,
        UUID classroomId,
        String actorType,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String requestId,
        String correlationId,
        Map<String, Object> beforeSummary,
        Map<String, Object> afterSummary,
        Instant occurredAt,
        Instant retentionUntil
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = classroomId;
        this.actorType = requiredText(actorType, "actorType");
        this.actorId = actorId;
        this.action = requiredText(action, "action");
        this.targetType = requiredText(targetType, "targetType");
        this.targetId = targetId;
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.beforeSummary = copy(beforeSummary);
        this.afterSummary = copy(afterSummary);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.retentionUntil = Objects.requireNonNull(
            retentionUntil,
            "retentionUntil must not be null"
        );
        if (retentionUntil.isBefore(occurredAt)) {
            throw new IllegalArgumentException("retentionUntil must not precede occurredAt");
        }
    }

    public static AuditEvent record(
        UUID id,
        UUID classroomId,
        String actorType,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String requestId,
        String correlationId,
        Map<String, Object> beforeSummary,
        Map<String, Object> afterSummary,
        Instant occurredAt,
        Instant retentionUntil
    ) {
        return new AuditEvent(
            id,
            classroomId,
            actorType,
            actorId,
            action,
            targetType,
            targetId,
            requestId,
            correlationId,
            beforeSummary,
            afterSummary,
            occurredAt,
            retentionUntil
        );
    }

    public UUID getId() {
        return id;
    }

    public String getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Map<String, Object> getBeforeSummary() {
        return beforeSummary == null ? null : Map.copyOf(beforeSummary);
    }

    public Map<String, Object> getAfterSummary() {
        return afterSummary == null ? null : Map.copyOf(afterSummary);
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? null : new LinkedHashMap<>(value);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
