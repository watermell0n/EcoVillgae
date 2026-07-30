package com.b201.ecovillage.store.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "store_product_versions")
public class StoreProductVersion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "store_product_id", nullable = false, updatable = false)
    private UUID storeProductId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_kind", nullable = false, updatable = false, length = 40)
    private StoreProductKind productKind;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(name = "vat_rate_bps", nullable = false, updatable = false)
    private int vatRateBps;

    @Column(nullable = false, updatable = false)
    private boolean purchasable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> metadata;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoreProductVersion() {
    }

    private StoreProductVersion(
        UUID id,
        UUID classroomId,
        UUID storeProductId,
        int version,
        StoreProductKind productKind,
        long unitPrice,
        int vatRateBps,
        boolean purchasable,
        Map<String, Object> metadata,
        Instant publishedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.storeProductId = Objects.requireNonNull(
            storeProductId,
            "storeProductId must not be null"
        );
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        if (vatRateBps < 0 || vatRateBps > 10_000) {
            throw new IllegalArgumentException("vatRateBps must be between 0 and 10000");
        }
        this.version = version;
        this.productKind = Objects.requireNonNull(productKind, "productKind must not be null");
        this.unitPrice = unitPrice;
        this.vatRateBps = vatRateBps;
        this.purchasable = purchasable;
        this.metadata = new LinkedHashMap<>(
            metadata == null ? Map.of() : metadata
        );
        this.publishedAt = Objects.requireNonNull(
            publishedAt,
            "publishedAt must not be null"
        );
        this.createdAt = publishedAt;
    }

    public static StoreProductVersion publish(
        UUID id,
        UUID classroomId,
        UUID storeProductId,
        int version,
        StoreProductKind productKind,
        long unitPrice,
        int vatRateBps,
        boolean purchasable,
        Map<String, Object> metadata,
        Instant publishedAt
    ) {
        return new StoreProductVersion(
            id,
            classroomId,
            storeProductId,
            version,
            productKind,
            unitPrice,
            vatRateBps,
            purchasable,
            metadata,
            publishedAt
        );
    }

    public void retire(Instant changedAt) {
        if (retiredAt != null) {
            throw new IllegalStateException("product version is already retired");
        }
        retiredAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public boolean isPurchasableAt(Instant now) {
        return purchasable
            && !publishedAt.isAfter(now)
            && (retiredAt == null || now.isBefore(retiredAt));
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getStoreProductId() {
        return storeProductId;
    }

    public int getVersion() {
        return version;
    }

    public StoreProductKind getProductKind() {
        return productKind;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getVatRateBps() {
        return vatRateBps;
    }

    public boolean isPurchasable() {
        return purchasable;
    }

    public Map<String, Object> getMetadata() {
        return Map.copyOf(metadata);
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }
}
