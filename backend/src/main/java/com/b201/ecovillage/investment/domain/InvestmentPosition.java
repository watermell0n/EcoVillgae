package com.b201.ecovillage.investment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "investment_positions")
public class InvestmentPosition {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;
    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;
    @Column(name = "investment_asset_id", nullable = false, updatable = false)
    private UUID investmentAssetId;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "average_price", nullable = false)
    private long averagePrice;
    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InvestmentPosition() {
    }

    public static InvestmentPosition empty(
        UUID id, UUID classroomId, UUID studentId, UUID assetId, Instant now
    ) {
        InvestmentPosition position = new InvestmentPosition();
        position.id = Objects.requireNonNull(id);
        position.classroomId = Objects.requireNonNull(classroomId);
        position.classroomStudentId = Objects.requireNonNull(studentId);
        position.investmentAssetId = Objects.requireNonNull(assetId);
        position.createdAt = Objects.requireNonNull(now);
        position.updatedAt = now;
        return position;
    }

    public void buy(int addedQuantity, long unitPrice, Instant now) {
        if (addedQuantity < 1 || unitPrice < 1) {
            throw new IllegalArgumentException("Buy values are invalid");
        }
        long previousCost = Math.multiplyExact((long) quantity, averagePrice);
        long addedCost = Math.multiplyExact((long) addedQuantity, unitPrice);
        int nextQuantity = Math.addExact(quantity, addedQuantity);
        averagePrice = Math.addExact(previousCost, addedCost) / nextQuantity;
        quantity = nextQuantity;
        updatedAt = Objects.requireNonNull(now);
    }

    public int getQuantity() { return quantity; }
    public long getAveragePrice() { return averagePrice; }
    public long getStateVersion() { return stateVersion; }
}
