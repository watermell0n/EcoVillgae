package com.b201.ecovillage.finance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "savings_installments")
public class SavingsInstallment {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(name = "classroom_id", nullable = false, updatable = false) private UUID classroomId;
    @Column(name = "savings_account_id", nullable = false, updatable = false) private UUID savingsAccountId;
    @Column(name = "installment_number", nullable = false, updatable = false) private int installmentNumber;
    @Column(name = "scheduled_amount", nullable = false, updatable = false) private long scheduledAmount;
    @Column(name = "due_at", nullable = false, updatable = false) private Instant dueAt;
    @Column(name = "retryable_until", nullable = false, updatable = false) private Instant retryableUntil;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "transaction_group_id") private UUID transactionGroupId;
    @Column(nullable = false, length = 40) private String status;
    @Version @Column(name = "state_version", nullable = false) private long stateVersion;
    @Column(name = "status_changed_at", nullable = false) private Instant statusChangedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SavingsInstallment() { }

    private SavingsInstallment(UUID id, UUID classroomId, UUID accountId, int number, long amount,
        Instant dueAt, Instant retryableUntil, String status, Instant paidAt, UUID transactionGroupId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.savingsAccountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.installmentNumber = number;
        this.scheduledAmount = amount;
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt must not be null");
        this.retryableUntil = Objects.requireNonNull(retryableUntil, "retryableUntil must not be null");
        this.status = status;
        this.paidAt = paidAt;
        this.transactionGroupId = transactionGroupId;
        this.statusChangedAt = createdAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static SavingsInstallment paid(UUID id, UUID classroomId, UUID accountId, long amount, Instant now, UUID groupId) {
        return new SavingsInstallment(id, classroomId, accountId, 1, amount, now, now, "PAID", now, groupId, now);
    }

    public static SavingsInstallment scheduled(UUID id, UUID classroomId, UUID accountId, int number,
        long amount, Instant dueAt, Instant retryableUntil, Instant createdAt) {
        return new SavingsInstallment(id, classroomId, accountId, number, amount, dueAt, retryableUntil,
            "SCHEDULED", null, null, createdAt);
    }
}
