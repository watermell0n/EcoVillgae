package com.b201.ecovillage.ai.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "async_operations")
public class AsyncOperation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "operation_type", nullable = false, updatable = false, length = 40)
    private String operationType;

    @Column(name = "requested_by_user_id", updatable = false)
    private UUID requestedByUserId;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(name = "result_location", length = 512)
    private String resultLocation;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

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

    protected AsyncOperation() {
    }

    public static AsyncOperation running(
        UUID id,
        UUID classroomId,
        UUID requestedByUserId,
        String operationType,
        Instant now
    ) {
        AsyncOperation operation = new AsyncOperation();
        operation.id = Objects.requireNonNull(id);
        operation.classroomId = Objects.requireNonNull(classroomId);
        operation.requestedByUserId = requestedByUserId;
        operation.operationType = Objects.requireNonNull(operationType);
        operation.progressPercent = 10;
        operation.startedAt = Objects.requireNonNull(now);
        operation.status = "RUNNING";
        operation.statusChangedAt = now;
        operation.createdAt = now;
        operation.updatedAt = now;
        return operation;
    }

    public void complete(String resultLocation, Instant now) {
        this.progressPercent = 100;
        this.resultLocation = Objects.requireNonNull(resultLocation);
        this.completedAt = Objects.requireNonNull(now);
        this.expiresAt = now.plusSeconds(30L * 24L * 60L * 60L);
        this.status = "COMPLETED";
        this.statusChangedAt = now;
        this.updatedAt = now;
    }

    public void updateProgress(int progressPercent, Instant now) {
        if (progressPercent < 0 || progressPercent > 99) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 99.");
        }
        if (!"RUNNING".equals(status)) {
            throw new IllegalStateException("Only running operations can report progress.");
        }
        this.progressPercent = progressPercent;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void fail(String errorCode, String errorDetail, Instant now) {
        this.errorCode = required(errorCode, 80);
        this.errorDetail = errorDetail;
        this.completedAt = Objects.requireNonNull(now);
        this.status = "FAILED";
        this.statusChangedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getStatus() {
        return status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public String getResultLocation() {
        return resultLocation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private static String required(String value, int maximumLength) {
        String required = Objects.requireNonNull(value).trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank.");
        }
        return required.substring(0, Math.min(required.length(), maximumLength));
    }
}
