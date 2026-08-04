package com.b201.ecovillage.finance.domain;

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
@Table(name = "deposit_accounts")
public class DepositAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @Column(name = "principal_amount", nullable = false, updatable = false)
    private long principalAmount;

    @Column(name = "expected_interest_amount", nullable = false, updatable = false)
    private long expectedInterestAmount;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "matures_at", nullable = false, updatable = false)
    private Instant maturesAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_transaction_group_id", nullable = false, updatable = false)
    private UUID openingTransactionGroupId;

    @Column(name = "closing_transaction_group_id")
    private UUID closingTransactionGroupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DepositAccountStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DepositAccount() {
    }

    private DepositAccount(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID productVersionId,
        long principalAmount,
        long expectedInterestAmount,
        Instant openedAt,
        Instant maturesAt,
        UUID openingTransactionGroupId
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.productVersionId = Objects.requireNonNull(
            productVersionId,
            "productVersionId must not be null"
        );
        if (principalAmount <= 0 || expectedInterestAmount < 0) {
            throw new IllegalArgumentException("deposit amounts are invalid");
        }
        this.principalAmount = principalAmount;
        this.expectedInterestAmount = expectedInterestAmount;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        this.maturesAt = Objects.requireNonNull(maturesAt, "maturesAt must not be null");
        if (maturesAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("maturesAt must not precede openedAt");
        }
        this.openingTransactionGroupId = Objects.requireNonNull(
            openingTransactionGroupId,
            "openingTransactionGroupId must not be null"
        );
        this.status = DepositAccountStatus.ACTIVE;
        this.statusChangedAt = openedAt;
        this.createdAt = openedAt;
        this.updatedAt = openedAt;
    }

    public static DepositAccount open(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID productVersionId,
        long principalAmount,
        long expectedInterestAmount,
        Instant openedAt,
        Instant maturesAt,
        UUID openingTransactionGroupId
    ) {
        return new DepositAccount(
            id,
            classroomId,
            classroomStudentId,
            productVersionId,
            principalAmount,
            expectedInterestAmount,
            openedAt,
            maturesAt,
            openingTransactionGroupId
        );
    }

    public void mature(UUID closingTransactionGroupId, Instant closedAt) {
        if (status != DepositAccountStatus.ACTIVE) {
            throw new IllegalStateException("only active deposit can mature");
        }
        if (closedAt.isBefore(maturesAt)) {
            throw new IllegalStateException("deposit has not reached maturity");
        }
        this.closingTransactionGroupId = Objects.requireNonNull(
            closingTransactionGroupId,
            "closingTransactionGroupId must not be null"
        );
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
        this.status = DepositAccountStatus.MATURED;
        this.statusChangedAt = closedAt;
        this.updatedAt = closedAt;
    }

    public void earlyTerminate(UUID closingTransactionGroupId, Instant closedAt) {
        if (status != DepositAccountStatus.ACTIVE) {
            throw new IllegalStateException("only active deposit can be terminated");
        }
        if (!closedAt.isBefore(maturesAt)) {
            throw new IllegalStateException("matured deposit cannot be terminated early");
        }
        this.closingTransactionGroupId = Objects.requireNonNull(
            closingTransactionGroupId,
            "closingTransactionGroupId must not be null"
        );
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
        this.status = DepositAccountStatus.EARLY_TERMINATED;
        this.statusChangedAt = closedAt;
        this.updatedAt = closedAt;
    }

    public long maturityAmount() {
        return Math.addExact(principalAmount, expectedInterestAmount);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public UUID getProductVersionId() {
        return productVersionId;
    }

    public long getPrincipalAmount() {
        return principalAmount;
    }

    public long getExpectedInterestAmount() {
        return expectedInterestAmount;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getMaturesAt() {
        return maturesAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public UUID getClosingTransactionGroupId() {
        return closingTransactionGroupId;
    }

    public DepositAccountStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
