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
@Table(name = "savings_accounts")
public class SavingsAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;
    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;
    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;
    @Column(name = "installment_amount", nullable = false, updatable = false)
    private long installmentAmount;
    @Column(name = "installment_count", nullable = false, updatable = false)
    private int installmentCount;
    @Column(name = "paid_installment_count", nullable = false)
    private int paidInstallmentCount;
    @Column(name = "principal_balance", nullable = false)
    private long principalBalance;
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
    private SavingsAccountStatus status;
    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavingsAccount() { }

    private SavingsAccount(UUID id, UUID classroomId, UUID classroomStudentId, UUID productVersionId,
        long installmentAmount, int installmentCount, long expectedInterestAmount, Instant openedAt,
        Instant maturesAt, UUID openingTransactionGroupId) {
        if (installmentAmount <= 0 || installmentCount < 2 || expectedInterestAmount < 0) {
            throw new IllegalArgumentException("savings terms are invalid");
        }
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(classroomStudentId, "classroomStudentId must not be null");
        this.productVersionId = Objects.requireNonNull(productVersionId, "productVersionId must not be null");
        this.installmentAmount = installmentAmount;
        this.installmentCount = installmentCount;
        this.paidInstallmentCount = 1;
        this.principalBalance = installmentAmount;
        this.expectedInterestAmount = expectedInterestAmount;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        this.maturesAt = Objects.requireNonNull(maturesAt, "maturesAt must not be null");
        this.openingTransactionGroupId = Objects.requireNonNull(openingTransactionGroupId, "openingTransactionGroupId must not be null");
        this.status = SavingsAccountStatus.ACTIVE;
        this.statusChangedAt = openedAt;
        this.createdAt = openedAt;
        this.updatedAt = openedAt;
    }

    public static SavingsAccount open(UUID id, UUID classroomId, UUID classroomStudentId, UUID productVersionId,
        long installmentAmount, int installmentCount, long expectedInterestAmount, Instant openedAt,
        Instant maturesAt, UUID openingTransactionGroupId) {
        return new SavingsAccount(id, classroomId, classroomStudentId, productVersionId, installmentAmount,
            installmentCount, expectedInterestAmount, openedAt, maturesAt, openingTransactionGroupId);
    }

    public UUID getId() { return id; }
    public UUID getClassroomId() { return classroomId; }
    public UUID getClassroomStudentId() { return classroomStudentId; }
    public UUID getProductVersionId() { return productVersionId; }
    public long getInstallmentAmount() { return installmentAmount; }
    public int getInstallmentCount() { return installmentCount; }
    public int getPaidInstallmentCount() { return paidInstallmentCount; }
    public long getPrincipalBalance() { return principalBalance; }
    public long getExpectedInterestAmount() { return expectedInterestAmount; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getMaturesAt() { return maturesAt; }
    public Instant getClosedAt() { return closedAt; }
    public SavingsAccountStatus getStatus() { return status; }
    public long getStateVersion() { return stateVersion; }

    public void recordInstallment(Instant paidAt) {
        if (status != SavingsAccountStatus.ACTIVE || paidInstallmentCount >= installmentCount) throw new IllegalStateException("savings account cannot accept another installment");
        paidInstallmentCount++;
        principalBalance = Math.addExact(principalBalance, installmentAmount);
        updatedAt = Objects.requireNonNull(paidAt, "paidAt must not be null");
    }

    public boolean isFullyPaid() { return paidInstallmentCount == installmentCount; }

    public long maturityAmount() { return Math.addExact(principalBalance, isFullyPaid() ? expectedInterestAmount : 0); }

    public void mature(UUID groupId, Instant closedAt) {
        if (status != SavingsAccountStatus.ACTIVE || closedAt.isBefore(maturesAt)) throw new IllegalStateException("savings account has not reached maturity");
        closingTransactionGroupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
        status = SavingsAccountStatus.MATURED;
        statusChangedAt = closedAt;
        updatedAt = closedAt;
    }
}
