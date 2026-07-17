package com.b201.bibim.domain.investment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "investment_orders")
public class InvestmentOrderEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "scenario_id")
    private UUID scenarioId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected InvestmentOrderEntity() {
    }

    public static InvestmentOrderEntity draft(
            UUID roomId,
            UUID sessionId,
            UUID studentId,
            long totalAmount
    ) {
        InvestmentOrderEntity order = new InvestmentOrderEntity();
        order.id = UUID.randomUUID();
        order.roomId = roomId;
        order.sessionId = sessionId;
        order.studentId = studentId;
        order.createdAt = Instant.now();
        order.changeDraft(totalAmount);
        return order;
    }

    public void changeDraft(long totalAmount) {
        if ("CONFIRMED".equals(status)) {
            throw new IllegalStateException("확정된 투자는 변경할 수 없습니다.");
        }
        this.totalAmount = totalAmount;
        this.status = "DRAFT";
        this.updatedAt = Instant.now();
    }

    public void cancelDraft() {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("투자 초안만 취소할 수 있습니다.");
        }
        this.status = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void confirm(UUID scenarioId, UUID transactionId) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("투자 초안만 확정할 수 있습니다.");
        }
        this.scenarioId = scenarioId;
        this.transactionId = transactionId;
        this.status = "CONFIRMED";
        this.confirmedAt = Instant.now();
        this.updatedAt = this.confirmedAt;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getStudentId() { return studentId; }
    public UUID getScenarioId() { return scenarioId; }
    public UUID getTransactionId() { return transactionId; }
    public long getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
