package com.b201.ecovillage.ledger.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "transaction_group_id", nullable = false, updatable = false)
    private UUID transactionGroupId;

    @Column(name = "ledger_account_id", nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Column(name = "account_sequence", nullable = false, updatable = false)
    private long accountSequence;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "balance_before", nullable = false, updatable = false)
    private long balanceBefore;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(
        UUID id,
        UUID classroomId,
        UUID transactionGroupId,
        UUID ledgerAccountId,
        LedgerAccountPosting posting,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.transactionGroupId = Objects.requireNonNull(
            transactionGroupId,
            "transactionGroupId must not be null"
        );
        this.ledgerAccountId = Objects.requireNonNull(
            ledgerAccountId,
            "ledgerAccountId must not be null"
        );
        LedgerAccountPosting validPosting = Objects.requireNonNull(posting, "posting must not be null");
        if (validPosting.accountSequence() < 1) {
            throw new IllegalArgumentException("account sequence must be positive");
        }
        if (validPosting.amount() == 0) {
            throw new IllegalArgumentException("ledger amount must not be zero");
        }
        if (Math.addExact(validPosting.balanceBefore(), validPosting.amount())
            != validPosting.balanceAfter()) {
            throw new IllegalArgumentException("ledger balance snapshot is inconsistent");
        }
        this.accountSequence = validPosting.accountSequence();
        this.amount = validPosting.amount();
        this.balanceBefore = validPosting.balanceBefore();
        this.balanceAfter = validPosting.balanceAfter();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static LedgerEntry post(
        UUID id,
        UUID classroomId,
        UUID transactionGroupId,
        UUID ledgerAccountId,
        LedgerAccountPosting posting,
        Instant createdAt
    ) {
        return new LedgerEntry(
            id,
            classroomId,
            transactionGroupId,
            ledgerAccountId,
            posting,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getTransactionGroupId() {
        return transactionGroupId;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public long getAccountSequence() {
        return accountSequence;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceBefore() {
        return balanceBefore;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }
}
