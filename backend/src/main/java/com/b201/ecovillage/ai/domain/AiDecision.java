package com.b201.ecovillage.ai.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_decisions")
public class AiDecision {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "ai_proposal_id", nullable = false, updatable = false)
    private UUID aiProposalId;

    @Column(name = "decided_by_user_id", nullable = false, updatable = false)
    private UUID decidedByUserId;

    @Column(nullable = false, updatable = false, length = 40)
    private String decision;

    @Column(updatable = false)
    private String reason;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiDecision() {
    }

    public static AiDecision create(
        UUID id,
        UUID classroomId,
        UUID proposalId,
        UUID teacherId,
        String decision,
        String reason,
        Instant now
    ) {
        AiDecision value = new AiDecision();
        value.id = Objects.requireNonNull(id);
        value.classroomId = Objects.requireNonNull(classroomId);
        value.aiProposalId = Objects.requireNonNull(proposalId);
        value.decidedByUserId = Objects.requireNonNull(teacherId);
        value.decision = Objects.requireNonNull(decision);
        value.reason = reason;
        value.decidedAt = Objects.requireNonNull(now);
        value.createdAt = now;
        return value;
    }

    public UUID getAiProposalId() {
        return aiProposalId;
    }

    public String getDecision() {
        return decision;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
