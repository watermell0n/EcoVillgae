package com.b201.bibim.domain.deposit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "deposits")
public class DepositEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false)
    private long principal;

    @Column(name = "interest_rate_bps", nullable = false)
    private int interestRateBps;

    @Column(name = "expected_interest", nullable = false)
    private long expectedInterest;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected DepositEntity() {
    }

    public static DepositEntity draft(
            UUID roomId,
            UUID sessionId,
            UUID studentId,
            long principal,
            int interestRateBps,
            long expectedInterest
    ) {
        DepositEntity deposit = new DepositEntity();
        deposit.id = UUID.randomUUID();
        deposit.roomId = roomId;
        deposit.sessionId = sessionId;
        deposit.studentId = studentId;
        deposit.createdAt = Instant.now();
        deposit.changeDraft(principal, interestRateBps, expectedInterest);
        return deposit;
    }

    public void changeDraft(long principal, int interestRateBps, long expectedInterest) {
        if ("CONFIRMED".equals(status)) {
            throw new IllegalStateException("확정된 예금은 변경할 수 없습니다.");
        }
        this.principal = principal;
        this.interestRateBps = interestRateBps;
        this.expectedInterest = expectedInterest;
        this.status = "DRAFT";
        this.updatedAt = Instant.now();
    }

    public void cancelDraft() {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("예금 초안만 취소할 수 있습니다.");
        }
        this.status = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void confirm(UUID transactionId) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("예금 초안만 확정할 수 있습니다.");
        }
        this.transactionId = transactionId;
        this.status = "CONFIRMED";
        this.confirmedAt = Instant.now();
        this.updatedAt = this.confirmedAt;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getStudentId() { return studentId; }
    public UUID getTransactionId() { return transactionId; }
    public long getPrincipal() { return principal; }
    public int getInterestRateBps() { return interestRateBps; }
    public long getExpectedInterest() { return expectedInterest; }
    public String getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
