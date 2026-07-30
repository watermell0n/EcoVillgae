package com.b201.ecovillage.ledger.domain;

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
@Table(name = "transaction_groups")
public class TransactionGroup {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "transaction_type", nullable = false, updatable = false, length = 40)
    private String transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 40)
    private LedgerActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "policy_snapshot_id", updatable = false)
    private UUID policySnapshotId;

    @Column(name = "recovery_of_group_id", updatable = false)
    private UUID recoveryOfGroupId;

    @Column(name = "root_transaction_group_id", nullable = false, updatable = false)
    private UUID rootTransactionGroupId;

    @Column(name = "request_id", nullable = false, updatable = false, length = 128)
    private String requestId;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    private String correlationId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TransactionGroupStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionGroup() {
    }

    private TransactionGroup(
        UUID id,
        UUID classroomId,
        String transactionType,
        LedgerActorType actorType,
        UUID actorId,
        UUID policySnapshotId,
        UUID recoveryOfGroupId,
        UUID rootTransactionGroupId,
        String requestId,
        String correlationId,
        String reason,
        Instant receivedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.transactionType = requiredText(transactionType, 40, "transactionType");
        this.actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        this.actorId = actorId;
        this.policySnapshotId = policySnapshotId;
        this.recoveryOfGroupId = recoveryOfGroupId;
        this.rootTransactionGroupId = Objects.requireNonNull(
            rootTransactionGroupId,
            "rootTransactionGroupId must not be null"
        );
        validateRecoveryLink();
        this.requestId = requiredText(requestId, 128, "requestId");
        this.correlationId = requiredText(correlationId, 128, "correlationId");
        this.reason = normalizedReason(reason);
        this.status = TransactionGroupStatus.RECEIVED;
        this.stateVersion = 0;
        this.statusChangedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.createdAt = receivedAt;
        this.updatedAt = receivedAt;
    }

    public static TransactionGroup receive(
        UUID id,
        UUID classroomId,
        String transactionType,
        LedgerActorType actorType,
        UUID actorId,
        UUID policySnapshotId,
        String requestId,
        String correlationId,
        String reason,
        Instant receivedAt
    ) {
        return new TransactionGroup(
            id,
            classroomId,
            transactionType,
            actorType,
            actorId,
            policySnapshotId,
            null,
            id,
            requestId,
            correlationId,
            reason,
            receivedAt
        );
    }

    public static TransactionGroup receiveRecovery(
        UUID id,
        UUID classroomId,
        String transactionType,
        LedgerActorType actorType,
        UUID actorId,
        UUID policySnapshotId,
        UUID recoveryOfGroupId,
        UUID rootTransactionGroupId,
        String requestId,
        String correlationId,
        String reason,
        Instant receivedAt
    ) {
        return new TransactionGroup(
            id,
            classroomId,
            transactionType,
            actorType,
            actorId,
            policySnapshotId,
            Objects.requireNonNull(recoveryOfGroupId, "recoveryOfGroupId must not be null"),
            rootTransactionGroupId,
            requestId,
            correlationId,
            reason,
            receivedAt
        );
    }

    public void startValidation(Instant changedAt) {
        transition(TransactionGroupStatus.RECEIVED, TransactionGroupStatus.VALIDATING, changedAt);
    }

    public void commit(Instant changedAt) {
        transition(TransactionGroupStatus.VALIDATING, TransactionGroupStatus.COMMITTED, changedAt);
        committedAt = changedAt;
    }

    public void reject(String rejectionReason, Instant changedAt) {
        transition(TransactionGroupStatus.VALIDATING, TransactionGroupStatus.REJECTED, changedAt);
        reason = requiredReason(rejectionReason);
        rejectedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public LedgerActorType getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getPolicySnapshotId() {
        return policySnapshotId;
    }

    public UUID getRecoveryOfGroupId() {
        return recoveryOfGroupId;
    }

    public UUID getRootTransactionGroupId() {
        return rootTransactionGroupId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getReason() {
        return reason;
    }

    public TransactionGroupStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    private void transition(
        TransactionGroupStatus expected,
        TransactionGroupStatus next,
        Instant changedAt
    ) {
        if (status != expected) {
            throw new IllegalStateException(
                "transaction group cannot transition from " + status + " to " + next
            );
        }
        Instant validChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        status = next;
        statusChangedAt = validChangedAt;
        updatedAt = validChangedAt;
    }

    private void validateRecoveryLink() {
        if (recoveryOfGroupId == null && !id.equals(rootTransactionGroupId)) {
            throw new IllegalArgumentException("original transaction must be its own root");
        }
        if (recoveryOfGroupId != null
            && (id.equals(recoveryOfGroupId) || id.equals(rootTransactionGroupId))) {
            throw new IllegalArgumentException("recovery transaction links are inconsistent");
        }
    }

    private static String normalizedReason(String value) {
        return value == null ? null : value.trim();
    }

    private static String requiredReason(String value) {
        String normalized = normalizedReason(value);
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("rejection reason must not be blank");
        }
        return normalized;
    }

    private static String requiredText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
