package com.b201.ecovillage.ledger.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_business_refs")
public class TransactionBusinessRef {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "transaction_group_id", nullable = false, updatable = false)
    private UUID transactionGroupId;

    @Column(name = "ref_type", nullable = false, updatable = false, length = 40)
    private String refType;

    @Column(name = "ref_id", nullable = false, updatable = false)
    private UUID refId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionBusinessRef() {
    }

    private TransactionBusinessRef(
        UUID id,
        UUID classroomId,
        UUID transactionGroupId,
        String refType,
        UUID refId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.transactionGroupId = Objects.requireNonNull(
            transactionGroupId,
            "transactionGroupId must not be null"
        );
        if (refType == null || refType.isBlank() || refType.length() > 40) {
            throw new IllegalArgumentException("refType is invalid");
        }
        this.refType = refType;
        this.refId = Objects.requireNonNull(refId, "refId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static TransactionBusinessRef create(
        UUID id,
        UUID classroomId,
        UUID transactionGroupId,
        String refType,
        UUID refId,
        Instant createdAt
    ) {
        return new TransactionBusinessRef(
            id,
            classroomId,
            transactionGroupId,
            refType,
            refId,
            createdAt
        );
    }

    public UUID getTransactionGroupId() {
        return transactionGroupId;
    }
}
