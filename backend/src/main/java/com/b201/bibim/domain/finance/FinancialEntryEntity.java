package com.b201.bibim.domain.finance;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_entries")
public class FinancialEntryEntity {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_before", nullable = false)
    private long balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "entry_order", nullable = false)
    private int entryOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FinancialEntryEntity() {
    }

    public static FinancialEntryEntity create(
            UUID transactionId,
            AccountEntity account,
            long amount,
            long balanceBefore,
            int entryOrder
    ) {
        FinancialEntryEntity entry = new FinancialEntryEntity();
        entry.id = UUID.randomUUID();
        entry.transactionId = transactionId;
        entry.accountId = account.getId();
        entry.amount = amount;
        entry.balanceBefore = balanceBefore;
        entry.balanceAfter = account.getBalance();
        entry.entryOrder = entryOrder;
        entry.createdAt = Instant.now();
        return entry;
    }
}
