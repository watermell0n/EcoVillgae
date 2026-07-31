package com.b201.ecovillage.vote.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vote_ballots")
public class VoteBallot {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "vote_id", nullable = false, updatable = false)
    private UUID voteId;

    @Column(name = "vote_option_id", nullable = false, updatable = false)
    private UUID voteOptionId;

    @Column(name = "eligibility_token_hash", nullable = false, updatable = false, length = 128)
    private String eligibilityTokenHash;

    @Column(name = "receipt_hash", nullable = false, updatable = false, length = 128)
    private String receiptHash;

    @Column(name = "cast_at", nullable = false, updatable = false)
    private Instant castAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VoteBallot() {
    }

    private VoteBallot(
        UUID id,
        UUID classroomId,
        UUID voteId,
        UUID voteOptionId,
        String eligibilityTokenHash,
        String receiptHash,
        Instant castAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.voteId = Objects.requireNonNull(voteId, "voteId must not be null");
        this.voteOptionId = Objects.requireNonNull(voteOptionId, "voteOptionId must not be null");
        this.eligibilityTokenHash = requiredHash(
            eligibilityTokenHash,
            "eligibilityTokenHash"
        );
        this.receiptHash = requiredHash(receiptHash, "receiptHash");
        this.castAt = Objects.requireNonNull(castAt, "castAt must not be null");
        this.createdAt = castAt;
    }

    public static VoteBallot cast(
        UUID id,
        UUID classroomId,
        UUID voteId,
        UUID voteOptionId,
        String eligibilityTokenHash,
        String receiptHash,
        Instant castAt
    ) {
        return new VoteBallot(
            id,
            classroomId,
            voteId,
            voteOptionId,
            eligibilityTokenHash,
            receiptHash,
            castAt
        );
    }

    public UUID getVoteOptionId() {
        return voteOptionId;
    }

    public String getReceiptHash() {
        return receiptHash;
    }

    public Instant getCastAt() {
        return castAt;
    }

    private static String requiredHash(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
