package com.b201.ecovillage.vote.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vote_options")
public class VoteOption {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "vote_id", nullable = false, updatable = false)
    private UUID voteId;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "budget_amount")
    private Long budgetAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VoteOption() {
    }

    private VoteOption(
        UUID id,
        UUID classroomId,
        UUID voteId,
        String label,
        String description,
        int sortOrder,
        long budgetAmount,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.voteId = Objects.requireNonNull(voteId, "voteId must not be null");
        this.label = requiredText(label, 160, "label");
        this.description = optionalText(description, 2_000);
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must not be negative");
        }
        if (budgetAmount < 0) {
            throw new IllegalArgumentException("budgetAmount must not be negative");
        }
        this.sortOrder = sortOrder;
        this.budgetAmount = budgetAmount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static VoteOption create(
        UUID id,
        UUID classroomId,
        UUID voteId,
        String label,
        String description,
        int sortOrder,
        long budgetAmount,
        Instant createdAt
    ) {
        return new VoteOption(
            id,
            classroomId,
            voteId,
            label,
            description,
            sortOrder,
            budgetAmount,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getVoteId() {
        return voteId;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public long getBudgetAmount() {
        return budgetAmount == null ? 0 : budgetAmount;
    }

    private static String requiredText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("description is too long");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
