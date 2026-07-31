package com.b201.ecovillage.store.domain;

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
@Table(name = "store_orders")
public class StoreOrder {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "subtotal_amount", nullable = false, updatable = false)
    private long subtotalAmount;

    @Column(name = "tax_amount", nullable = false, updatable = false)
    private long taxAmount;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private long totalAmount;

    @Column(name = "policy_snapshot_id", nullable = false, updatable = false)
    private UUID policySnapshotId;

    @Column(name = "transaction_group_id", updatable = false)
    private UUID transactionGroupId;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "operation_canceled_at")
    private Instant operationCanceledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StoreOrderStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StoreOrder() {
    }

    private StoreOrder(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        long subtotalAmount,
        long taxAmount,
        UUID policySnapshotId,
        UUID transactionGroupId,
        StoreOrderStatus status,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        if (subtotalAmount < 0 || taxAmount < 0) {
            throw new IllegalArgumentException("order amounts must not be negative");
        }
        this.subtotalAmount = subtotalAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = Math.addExact(subtotalAmount, taxAmount);
        this.policySnapshotId = Objects.requireNonNull(
            policySnapshotId,
            "policySnapshotId must not be null"
        );
        this.transactionGroupId = Objects.requireNonNull(
            transactionGroupId,
            "transactionGroupId must not be null"
        );
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static StoreOrder purchased(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        long subtotalAmount,
        long taxAmount,
        UUID policySnapshotId,
        UUID transactionGroupId,
        StoreProductKind productKind,
        Instant createdAt
    ) {
        StoreOrderStatus status = productKind == StoreProductKind.PHYSICAL
            ? StoreOrderStatus.PAID
            : StoreOrderStatus.COMPLETED;
        return new StoreOrder(
            id,
            classroomId,
            classroomStudentId,
            subtotalAmount,
            taxAmount,
            policySnapshotId,
            transactionGroupId,
            status,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public long getSubtotalAmount() {
        return subtotalAmount;
    }

    public long getTaxAmount() {
        return taxAmount;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public UUID getTransactionGroupId() {
        return transactionGroupId;
    }

    public StoreOrderStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
