package com.b201.ecovillage.store.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "store_stocks")
public class StoreStock {

    @Id
    @Column(name = "store_product_id", nullable = false, updatable = false)
    private UUID storeProductId;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StoreStock() {
    }

    private StoreStock(
        UUID storeProductId,
        UUID classroomId,
        int availableQuantity,
        Instant createdAt
    ) {
        this.storeProductId = Objects.requireNonNull(
            storeProductId,
            "storeProductId must not be null"
        );
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.availableQuantity = nonNegative(availableQuantity, "availableQuantity");
        this.reservedQuantity = 0;
        this.updatedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static StoreStock initialize(
        UUID storeProductId,
        UUID classroomId,
        int availableQuantity,
        Instant createdAt
    ) {
        return new StoreStock(
            storeProductId,
            classroomId,
            availableQuantity,
            createdAt
        );
    }

    public void reserve(int quantity, Instant changedAt) {
        int requested = positive(quantity);
        if (availableQuantity < requested) {
            throw new InsufficientStoreStockException(availableQuantity, requested);
        }
        availableQuantity -= requested;
        reservedQuantity = Math.addExact(reservedQuantity, requested);
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void releaseReserved(int quantity, Instant changedAt) {
        int requested = positive(quantity);
        if (reservedQuantity < requested) {
            throw new IllegalStateException("reserved stock is smaller than release quantity");
        }
        reservedQuantity -= requested;
        availableQuantity = Math.addExact(availableQuantity, requested);
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void consumeReserved(int quantity, Instant changedAt) {
        int requested = positive(quantity);
        if (reservedQuantity < requested) {
            throw new IllegalStateException("reserved stock is smaller than consume quantity");
        }
        reservedQuantity -= requested;
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void consumeAvailable(int quantity, Instant changedAt) {
        int requested = positive(quantity);
        if (availableQuantity < requested) {
            throw new InsufficientStoreStockException(availableQuantity, requested);
        }
        availableQuantity -= requested;
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void restock(int quantity, Instant changedAt) {
        availableQuantity = Math.addExact(availableQuantity, positive(quantity));
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void replaceAvailableQuantity(int quantity, Instant changedAt) {
        availableQuantity = nonNegative(quantity, "availableQuantity");
        updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getStoreProductId() {
        return storeProductId;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private static int positive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return value;
    }

    private static int nonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
