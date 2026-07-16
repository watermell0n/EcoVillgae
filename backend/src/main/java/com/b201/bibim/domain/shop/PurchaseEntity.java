package com.b201.bibim.domain.shop;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchases")
public class PurchaseEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PurchaseEntity() {
    }

    public static PurchaseEntity success(
            UUID roomId,
            UUID studentId,
            UUID productId,
            UUID transactionId,
            int quantity,
            long unitPrice,
            long totalPrice
    ) {
        PurchaseEntity purchase = new PurchaseEntity();
        purchase.id = UUID.randomUUID();
        purchase.roomId = roomId;
        purchase.studentId = studentId;
        purchase.productId = productId;
        purchase.transactionId = transactionId;
        purchase.quantity = quantity;
        purchase.unitPrice = unitPrice;
        purchase.totalPrice = totalPrice;
        purchase.status = "SUCCESS";
        purchase.createdAt = Instant.now();
        return purchase;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getTotalPrice() {
        return totalPrice;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
