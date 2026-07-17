package com.b201.bibim.domain.fund;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fund_expenses")
public class FundExpenseEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "cost_option_id", nullable = false)
    private UUID costOptionId;

    @Column(name = "name_snapshot", nullable = false, length = 100)
    private String nameSnapshot;

    @Column(nullable = false)
    private long amount;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "applied_by", nullable = false)
    private UUID appliedBy;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    protected FundExpenseEntity() {
    }

    public static FundExpenseEntity applied(
            UUID roomId,
            FundCostOptionEntity option,
            UUID transactionId,
            UUID teacherId
    ) {
        FundExpenseEntity expense = new FundExpenseEntity();
        expense.id = UUID.randomUUID();
        expense.roomId = roomId;
        expense.costOptionId = option.getId();
        expense.nameSnapshot = option.getName();
        expense.amount = option.getAmount();
        expense.transactionId = transactionId;
        expense.appliedBy = teacherId;
        expense.appliedAt = Instant.now();
        return expense;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getCostOptionId() { return costOptionId; }
    public String getNameSnapshot() { return nameSnapshot; }
    public long getAmount() { return amount; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getAppliedAt() { return appliedAt; }
}

