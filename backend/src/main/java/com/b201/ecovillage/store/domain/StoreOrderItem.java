package com.b201.ecovillage.store.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_order_items")
public class StoreOrderItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "store_order_id", nullable = false, updatable = false)
    private UUID storeOrderId;

    @Column(name = "store_product_version_id", nullable = false, updatable = false)
    private UUID storeProductVersionId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(name = "vat_rate_bps", nullable = false, updatable = false)
    private int vatRateBps;

    @Column(name = "subtotal_amount", nullable = false, updatable = false)
    private long subtotalAmount;

    @Column(name = "tax_amount", nullable = false, updatable = false)
    private long taxAmount;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private long totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoreOrderItem() {
    }

    private StoreOrderItem(
        UUID id,
        UUID classroomId,
        UUID storeOrderId,
        UUID storeProductVersionId,
        int quantity,
        long unitPrice,
        int vatRateBps,
        long taxAmount,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.storeOrderId = Objects.requireNonNull(storeOrderId, "storeOrderId must not be null");
        this.storeProductVersionId = Objects.requireNonNull(
            storeProductVersionId,
            "storeProductVersionId must not be null"
        );
        if (quantity <= 0 || unitPrice < 0 || vatRateBps < 0 || vatRateBps > 10_000) {
            throw new IllegalArgumentException("order item price, tax, or quantity is invalid");
        }
        if (taxAmount < 0) {
            throw new IllegalArgumentException("taxAmount must not be negative");
        }
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRateBps = vatRateBps;
        this.subtotalAmount = Math.multiplyExact(unitPrice, quantity);
        this.taxAmount = taxAmount;
        this.totalAmount = Math.addExact(subtotalAmount, taxAmount);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static StoreOrderItem purchased(
        UUID id,
        UUID classroomId,
        UUID storeOrderId,
        UUID storeProductVersionId,
        int quantity,
        long unitPrice,
        int vatRateBps,
        long taxAmount,
        Instant createdAt
    ) {
        return new StoreOrderItem(
            id,
            classroomId,
            storeOrderId,
            storeProductVersionId,
            quantity,
            unitPrice,
            vatRateBps,
            taxAmount,
            createdAt
        );
    }

    public long getSubtotalAmount() {
        return subtotalAmount;
    }

    public UUID getId() {
        return id;
    }

    public long getTaxAmount() {
        return taxAmount;
    }

    public long getTotalAmount() {
        return totalAmount;
    }
}
