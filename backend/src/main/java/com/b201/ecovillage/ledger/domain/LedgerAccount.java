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
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false, length = 40)
    private LedgerOwnerType ownerType;

    @Column(name = "classroom_student_id", updatable = false)
    private UUID classroomStudentId;

    @Column(name = "system_owner_key", updatable = false, length = 80)
    private String systemOwnerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false, length = 40)
    private LedgerAccountType accountType;

    @Column(name = "current_balance", nullable = false)
    private long currentBalance;

    @Column(name = "next_entry_sequence", nullable = false)
    private long nextEntrySequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LedgerAccountStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerAccount() {
    }

    private LedgerAccount(
        UUID id,
        UUID classroomId,
        LedgerOwnerType ownerType,
        UUID classroomStudentId,
        String systemOwnerKey,
        LedgerAccountType accountType,
        Instant openedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType must not be null");
        this.classroomStudentId = classroomStudentId;
        this.systemOwnerKey = normalizedSystemOwnerKey(systemOwnerKey);
        this.accountType = Objects.requireNonNull(accountType, "accountType must not be null");
        validateOwnership();
        this.currentBalance = 0;
        this.nextEntrySequence = 1;
        this.status = LedgerAccountStatus.ACTIVE;
        this.stateVersion = 0;
        this.statusChangedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        this.createdAt = openedAt;
        this.updatedAt = openedAt;
    }

    public static LedgerAccount studentWallet(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        Instant openedAt
    ) {
        return new LedgerAccount(
            id,
            classroomId,
            LedgerOwnerType.STUDENT,
            Objects.requireNonNull(classroomStudentId, "classroomStudentId must not be null"),
            null,
            LedgerAccountType.STUDENT_WALLET,
            openedAt
        );
    }

    public static LedgerAccount systemAccount(
        UUID id,
        UUID classroomId,
        String systemOwnerKey,
        LedgerAccountType accountType,
        Instant openedAt
    ) {
        if (accountType == LedgerAccountType.STUDENT_WALLET) {
            throw new IllegalArgumentException("system account cannot be a student wallet");
        }
        return new LedgerAccount(
            id,
            classroomId,
            LedgerOwnerType.SYSTEM,
            null,
            systemOwnerKey,
            accountType,
            openedAt
        );
    }

    public LedgerAccountPosting post(long amount, Instant postedAt) {
        if (status != LedgerAccountStatus.ACTIVE) {
            throw new IllegalStateException("ledger account is not active");
        }
        if (amount == 0) {
            throw new IllegalArgumentException("ledger amount must not be zero");
        }

        long nextBalance = Math.addExact(currentBalance, amount);
        if (
            (
                accountType == LedgerAccountType.STUDENT_WALLET
                    || accountType == LedgerAccountType.CLASSROOM_FUND
            )
                && nextBalance < 0
        ) {
            throw new InsufficientLedgerBalanceException(id, currentBalance, -amount);
        }

        Instant changedAt = Objects.requireNonNull(postedAt, "postedAt must not be null");
        LedgerAccountPosting posting = new LedgerAccountPosting(
            nextEntrySequence,
            amount,
            currentBalance,
            nextBalance
        );
        currentBalance = nextBalance;
        nextEntrySequence = Math.incrementExact(nextEntrySequence);
        updatedAt = changedAt;
        return posting;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public LedgerOwnerType getOwnerType() {
        return ownerType;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public String getSystemOwnerKey() {
        return systemOwnerKey;
    }

    public LedgerAccountType getAccountType() {
        return accountType;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }

    public long getNextEntrySequence() {
        return nextEntrySequence;
    }

    public LedgerAccountStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private void validateOwnership() {
        boolean validStudent = ownerType == LedgerOwnerType.STUDENT
            && classroomStudentId != null
            && systemOwnerKey == null
            && accountType == LedgerAccountType.STUDENT_WALLET;
        boolean validSystem = ownerType == LedgerOwnerType.SYSTEM
            && classroomStudentId == null
            && systemOwnerKey != null;
        if (!validStudent && !validSystem) {
            throw new IllegalArgumentException("ledger account owner fields are inconsistent");
        }
    }

    private static String normalizedSystemOwnerKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("systemOwnerKey must contain 1 to 80 characters");
        }
        return normalized;
    }
}
