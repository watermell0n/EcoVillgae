package com.b201.ecovillage.store.domain;

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
@Table(name = "store_products")
public class StoreProduct {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_kind", nullable = false, updatable = false, length = 40)
    private StoreProductKind productKind;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StoreProductStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StoreProduct() {
    }

    private StoreProduct(
        UUID id,
        UUID classroomId,
        String name,
        String description,
        StoreProductKind productKind,
        UUID createdByUserId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.name = requiredText(name, 120, "name");
        this.description = normalizedDescription(description);
        this.productKind = Objects.requireNonNull(productKind, "productKind must not be null");
        this.createdByUserId = Objects.requireNonNull(
            createdByUserId,
            "createdByUserId must not be null"
        );
        this.status = StoreProductStatus.DRAFT;
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static StoreProduct draft(
        UUID id,
        UUID classroomId,
        String name,
        String description,
        StoreProductKind productKind,
        UUID createdByUserId,
        Instant createdAt
    ) {
        return new StoreProduct(
            id,
            classroomId,
            name,
            description,
            productKind,
            createdByUserId,
            createdAt
        );
    }

    public void updateDescription(String name, String description, Instant changedAt) {
        this.name = requiredText(name, 120, "name");
        this.description = normalizedDescription(description);
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void publish(Instant changedAt) {
        if (status == StoreProductStatus.PUBLISHED) {
            throw new IllegalStateException("product is already published");
        }
        changeStatus(StoreProductStatus.PUBLISHED, changedAt);
    }

    public void unpublish(Instant changedAt) {
        if (status != StoreProductStatus.PUBLISHED) {
            throw new IllegalStateException("only a published product can be unpublished");
        }
        changeStatus(StoreProductStatus.UNPUBLISHED, changedAt);
    }

    public boolean isPublished() {
        return status == StoreProductStatus.PUBLISHED;
    }

    public boolean canReviseDraft() {
        return status != StoreProductStatus.PUBLISHED;
    }

    private void changeStatus(StoreProductStatus next, Instant changedAt) {
        this.status = next;
        this.statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getName() {
        return name;
    }

    public StoreProductKind getProductKind() {
        return productKind;
    }

    public StoreProductStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
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

    private static String normalizedDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
