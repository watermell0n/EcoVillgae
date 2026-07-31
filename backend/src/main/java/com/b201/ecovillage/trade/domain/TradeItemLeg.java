package com.b201.ecovillage.trade.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_item_legs")
public class TradeItemLeg {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(name = "inventory_item_id", nullable = false, updatable = false)
    private UUID inventoryItemId;

    @Column(name = "from_student_id", nullable = false, updatable = false)
    private UUID fromStudentId;

    @Column(name = "to_student_id", nullable = false, updatable = false)
    private UUID toStudentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TradeItemLeg() {
    }

    private TradeItemLeg(
        UUID id,
        UUID classroomId,
        UUID tradeId,
        UUID inventoryItemId,
        UUID fromStudentId,
        UUID toStudentId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId must not be null");
        this.inventoryItemId = Objects.requireNonNull(
            inventoryItemId,
            "inventoryItemId must not be null"
        );
        this.fromStudentId = Objects.requireNonNull(
            fromStudentId,
            "fromStudentId must not be null"
        );
        this.toStudentId = Objects.requireNonNull(toStudentId, "toStudentId must not be null");
        if (fromStudentId.equals(toStudentId)) {
            throw new IllegalArgumentException("item leg students must be different");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static TradeItemLeg create(
        UUID id,
        UUID classroomId,
        UUID tradeId,
        UUID inventoryItemId,
        UUID fromStudentId,
        UUID toStudentId,
        Instant createdAt
    ) {
        return new TradeItemLeg(
            id,
            classroomId,
            tradeId,
            inventoryItemId,
            fromStudentId,
            toStudentId,
            createdAt
        );
    }

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public UUID getFromStudentId() {
        return fromStudentId;
    }

    public UUID getToStudentId() {
        return toStudentId;
    }
}
