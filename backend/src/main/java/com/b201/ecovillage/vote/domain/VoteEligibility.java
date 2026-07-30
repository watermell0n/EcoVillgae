package com.b201.ecovillage.vote.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vote_eligibilities")
public class VoteEligibility {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "vote_id", nullable = false, updatable = false)
    private UUID voteId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "eligibility_token_hash", nullable = false, updatable = false, length = 128)
    private String eligibilityTokenHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VoteEligibility() {
    }

    private VoteEligibility(
        UUID id,
        UUID classroomId,
        UUID voteId,
        UUID classroomStudentId,
        String eligibilityTokenHash,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.voteId = Objects.requireNonNull(voteId, "voteId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.eligibilityTokenHash = requiredHash(eligibilityTokenHash);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static VoteEligibility create(
        UUID id,
        UUID classroomId,
        UUID voteId,
        UUID classroomStudentId,
        String eligibilityTokenHash,
        Instant createdAt
    ) {
        return new VoteEligibility(
            id,
            classroomId,
            voteId,
            classroomStudentId,
            eligibilityTokenHash,
            createdAt
        );
    }

    public void use(Instant castAt) {
        if (usedAt != null) {
            throw new IllegalStateException("eligibility has already been used");
        }
        usedAt = Objects.requireNonNull(castAt, "castAt must not be null");
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public String getEligibilityTokenHash() {
        return eligibilityTokenHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    private static String requiredHash(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("eligibilityTokenHash is invalid");
        }
        return value;
    }
}
