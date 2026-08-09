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

    /**
     * 장기 투자에서 보유 수량 일부 또는 전부를 현재 공개 가격으로 매도한다.
     *
     * <p>평균 매수가는 남은 주식의 취득 단가이므로 일부 매도 때는 그대로 두고, 전량을
     * 매도했을 때만 0으로 비운다.
     */
    public void sell(int soldQuantity, Instant now) {
        if (soldQuantity < 1 || soldQuantity > quantity) {
            throw new IllegalArgumentException("Sell quantity exceeds the position");
        }
        quantity -= soldQuantity;
        if (quantity == 0) {
            averagePrice = 0;
        }
        updatedAt = Objects.requireNonNull(now);
    }

    /**
     * 보유 전량을 정산해 비운다. 정산한 수량을 돌려주므로 호출하는 쪽이 지급액을 계산한다.
     */
    public int settle(Instant now) {
        int settled = quantity;
        quantity = 0;
        averagePrice = 0;
        updatedAt = Objects.requireNonNull(now);
        return settled;
    }

    public UUID getId() { return id; }
    public UUID getClassroomStudentId() { return classroomStudentId; }
    public UUID getInvestmentAssetId() { return investmentAssetId; }
    public int getQuantity() { return quantity; }
    public long getAveragePrice() { return averagePrice; }
    public long getStateVersion() { return stateVersion; }
}
