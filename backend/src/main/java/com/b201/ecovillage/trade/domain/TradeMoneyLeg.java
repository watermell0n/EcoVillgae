package com.b201.ecovillage.trade.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_money_legs")
public class TradeMoneyLeg {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(name = "from_student_id", nullable = false, updatable = false)
    private UUID fromStudentId;

    @Column(name = "to_student_id", nullable = false, updatable = false)
    private UUID toStudentId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TradeMoneyLeg() {
    }

    private TradeMoneyLeg(
        UUID id,
        UUID classroomId,
        UUID tradeId,
        UUID fromStudentId,
        UUID toStudentId,
        long amount,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId must not be null");
        this.fromStudentId = Objects.requireNonNull(
            fromStudentId,
            "fromStudentId must not be null"
        );
        this.toStudentId = Objects.requireNonNull(toStudentId, "toStudentId must not be null");
        if (fromStudentId.equals(toStudentId) || amount <= 0) {
            throw new IllegalArgumentException("money leg direction or amount is invalid");
        }
        this.amount = amount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static TradeMoneyLeg create(
        UUID id,
        UUID classroomId,
        UUID tradeId,
        UUID fromStudentId,
        UUID toStudentId,
        long amount,
        Instant createdAt
    ) {
        return new TradeMoneyLeg(
            id,
            classroomId,
            tradeId,
            fromStudentId,
            toStudentId,
            amount,
            createdAt
        );
    }

    public UUID getFromStudentId() {
        return fromStudentId;
    }

    public UUID getToStudentId() {
        return toStudentId;
    }

    public long getAmount() {
        return amount;
    }
}
