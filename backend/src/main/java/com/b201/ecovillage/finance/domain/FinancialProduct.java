package com.b201.ecovillage.finance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "financial_products")
public class FinancialProduct {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, updatable = false, length = 40)
    private FinancialProductType productType;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 40)
    private String status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialProduct() {
    }

    private FinancialProduct(
        UUID id,
        UUID classroomId,
        FinancialProductType productType,
        String name,
        String description,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.productType = Objects.requireNonNull(productType, "productType must not be null");
        this.name = requiredName(name);
        this.description = normalized(description);
        this.status = "DRAFT";
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static FinancialProduct draft(
        UUID id,
        UUID classroomId,
        FinancialProductType productType,
        String name,
        String description,
        Instant createdAt
    ) {
        return new FinancialProduct(
            id,
            classroomId,
            productType,
            name,
            description,
            createdAt
        );
    }

    public void revise(String name, String description, Instant changedAt) {
        if ("STOPPED".equals(status)) {
            throw new IllegalStateException("stopped product cannot be revised");
        }
        this.name = requiredName(name);
        this.description = normalized(description);
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void publish(Instant changedAt) {
        status = "PUBLISHED";
        changeStatus(changedAt);
    }

    public void stop(Instant changedAt) {
        status = "STOPPED";
        changeStatus(changedAt);
    }

    public boolean isPublished() {
        return "PUBLISHED".equals(status);
    }

    private void changeStatus(Instant changedAt) {
        this.statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public FinancialProductType getProductType() {
        return productType;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private static String requiredName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("name exceeds 120 characters");
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
