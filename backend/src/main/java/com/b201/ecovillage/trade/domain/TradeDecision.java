package com.b201.ecovillage.trade.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_decisions")
public class TradeDecision {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(name = "decision_type", nullable = false, updatable = false, length = 40)
    private String decisionType;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 40)
    private String actorType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false, length = 40)
    private String decision;

    @Column(columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TradeDecision() {
    }

    private TradeDecision(
        UUID id,
        UUID classroomId,
        UUID tradeId,
        String decisionType,
        String actorType,
        UUID actorId,
        String decision,
        String reason,
        Instant decidedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId must not be null");
        this.decisionType = requiredText(decisionType, 40, "decisionType");
        this.actorType = requiredText(actorType, 40, "actorType");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.decision = requiredText(decision, 40, "decision");
        this.reason = normalized(reason);
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        this.createdAt = decidedAt;
    }

    public static TradeDecision record(
        UUID id,
        UUID classroomId,
        UUID tradeId,
        String decisionType,
        String actorType,
        UUID actorId,
        String decision,
        String reason,
        Instant decidedAt
    ) {
        return new TradeDecision(
            id,
            classroomId,
            tradeId,
            decisionType,
            actorType,
            actorId,
            decision,
            reason,
            decidedAt
        );
    }

    private static String requiredText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String normalized(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
