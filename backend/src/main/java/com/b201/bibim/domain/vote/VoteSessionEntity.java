package com.b201.bibim.domain.vote;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vote_sessions")
public class VoteSessionEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 200)
    private String question;

    @Column(name = "fund_balance_snapshot", nullable = false)
    private long fundBalanceSnapshot;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "decided_option_id")
    private UUID decidedOptionId;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VoteSessionEntity() {
    }

    public static VoteSessionEntity ready(UUID roomId, String question, long fundBalanceSnapshot) {
        VoteSessionEntity session = new VoteSessionEntity();
        session.id = UUID.randomUUID();
        session.roomId = roomId;
        session.question = question;
        session.fundBalanceSnapshot = fundBalanceSnapshot;
        session.status = "READY";
        session.createdAt = Instant.now();
        return session;
    }

    public void open() {
        if (!"READY".equals(status)) throw new IllegalStateException("준비 상태의 투표만 시작할 수 있습니다.");
        status = "OPEN";
        openedAt = Instant.now();
    }

    public void close() {
        if (!"OPEN".equals(status)) throw new IllegalStateException("진행 중인 투표만 마감할 수 있습니다.");
        status = "CLOSED";
        closedAt = Instant.now();
    }

    public void decide(UUID optionId, UUID teacherId) {
        if (!"CLOSED".equals(status)) throw new IllegalStateException("마감된 투표만 확정할 수 있습니다.");
        status = "DECIDED";
        decidedOptionId = optionId;
        decidedBy = teacherId;
        if (closedAt == null) closedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public String getQuestion() { return question; }
    public long getFundBalanceSnapshot() { return fundBalanceSnapshot; }
    public String getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public UUID getDecidedOptionId() { return decidedOptionId; }
    public UUID getDecidedBy() { return decidedBy; }
}

