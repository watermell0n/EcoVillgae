package com.b201.ecovillage.ai.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_runs")
public class AiRun {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "async_operation_id", updatable = false)
    private UUID asyncOperationId;

    @Column(name = "requested_by_user_id", updatable = false)
    private UUID requestedByUserId;

    @Column(name = "run_type", nullable = false, updatable = false, length = 40)
    private String runType;

    @Column(name = "trigger_type", nullable = false, updatable = false, length = 40)
    private String triggerType;

    @Column(name = "analysis_period_from")
    private Instant analysisPeriodFrom;

    @Column(name = "analysis_period_to")
    private Instant analysisPeriodTo;

    @Column(name = "model_provider", length = 40)
    private String modelProvider;

    @Column(name = "model_id", length = 120)
    private String modelId;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_scope", nullable = false, updatable = false)
    private Map<String, Object> inputScope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result")
    private Map<String, Object> validationResult;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "retention_until", nullable = false, updatable = false)
    private Instant retentionUntil;

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

    protected AiRun() {
    }

    public static AiRun generating(
        UUID id,
        UUID classroomId,
        UUID operationId,
        UUID requestedByUserId,
        String runType,
        String triggerType,
        String promptVersion,
        Map<String, Object> inputScope,
        Instant now
    ) {
        AiRun run = new AiRun();
        run.id = Objects.requireNonNull(id);
        run.classroomId = Objects.requireNonNull(classroomId);
        run.asyncOperationId = operationId;
        run.requestedByUserId = requestedByUserId;
        run.runType = Objects.requireNonNull(runType);
        run.triggerType = Objects.requireNonNull(triggerType);
        run.modelProvider = "PENDING";
        run.modelId = "PENDING";
        run.promptVersion = promptVersion;
        run.inputScope = new LinkedHashMap<>(inputScope);
        run.requestedAt = now;
        run.retentionUntil = now.plusSeconds(365L * 24L * 60L * 60L);
        run.status = "GENERATING";
        run.statusChangedAt = now;
        run.createdAt = now;
        run.updatedAt = now;
        return run;
    }

    public void setAnalysisPeriod(Instant from, Instant to) {
        this.analysisPeriodFrom = Objects.requireNonNull(from);
        this.analysisPeriodTo = Objects.requireNonNull(to);
    }

    public void insufficientData(Map<String, Object> validationResult, Instant now) {
        this.modelProvider = null;
        this.modelId = null;
        this.validationResult = new LinkedHashMap<>(validationResult);
        this.completedAt = now;
        transition("INSUFFICIENT_DATA", now);
    }

    public void completeWithoutModel(Map<String, Object> validationResult, Instant now) {
        this.modelProvider = null;
        this.modelId = null;
        this.validationResult = new LinkedHashMap<>(validationResult);
        this.completedAt = now;
        transition("COMPLETED", now);
    }

    public void awaitTeacher(
        String provider,
        String modelId,
        Map<String, Object> validationResult,
        Instant now
    ) {
        this.modelProvider = required(provider, 40);
        this.modelId = required(modelId, 120);
        this.validationResult = new LinkedHashMap<>(validationResult);
        this.completedAt = now;
        this.status = "AWAITING_TEACHER";
        this.statusChangedAt = now;
        this.updatedAt = now;
    }

    public void acknowledge(Instant now) {
        transition("ACKNOWLEDGED", now);
    }

    public void reject(Instant now) {
        transition("REJECTED", now);
    }

    public void approve(Instant now) {
        transition("APPROVED", now);
    }

    public void complete(Instant now) {
        this.completedAt = now;
        transition("COMPLETED", now);
    }

    public void applyFailed(String code, Instant now) {
        this.errorCode = code;
        transition("APPLY_FAILED", now);
    }

    private void transition(String status, Instant now) {
        this.status = status;
        this.statusChangedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getStatus() {
        return status;
    }

    private static String required(String value, int maximumLength) {
        return Objects.requireNonNull(value).substring(0, Math.min(value.length(), maximumLength));
    }
}
