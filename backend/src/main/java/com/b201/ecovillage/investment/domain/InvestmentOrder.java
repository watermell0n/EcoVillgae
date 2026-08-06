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
@Table(name = "investment_orders")
public class InvestmentOrder {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;
    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;
    @Column(name = "investment_asset_id", nullable = false, updatable = false)
    private UUID investmentAssetId;
    @Column(nullable = false, length = 10, updatable = false)
    private String side;
    @Column(nullable = false, updatable = false)
    private int quantity;
    @Column(name = "executed_price", updatable = false)
    private Long executedPrice;
    @Column(name = "executed_price_step", updatable = false)
    private Integer executedPriceStep;
    @Column(name = "transaction_group_id", updatable = false)
    private UUID transactionGroupId;
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;
    @Column(name = "executed_at", updatable = false)
    private Instant executedAt;
    @Column(name = "reject_reason", length = 80)
    private String rejectReason;
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

    protected InvestmentOrder() {
    }

    public static InvestmentOrder executedBuy(
        UUID id,
        UUID classroomId,
        UUID studentId,
        UUID assetId,
        int quantity,
        long price,
        int priceStep,
        UUID transactionGroupId,
        Instant now
    ) {
        if (quantity < 1 || price < 1 || priceStep < 1) {
            throw new IllegalArgumentException("Investment order values are invalid");
        }
        InvestmentOrder order = new InvestmentOrder();
        order.id = Objects.requireNonNull(id);
        order.classroomId = Objects.requireNonNull(classroomId);
        order.classroomStudentId = Objects.requireNonNull(studentId);
        order.investmentAssetId = Objects.requireNonNull(assetId);
        order.side = "BUY";
        order.quantity = quantity;
        order.executedPrice = price;
        order.executedPriceStep = priceStep;
        order.transactionGroupId = Objects.requireNonNull(transactionGroupId);
        order.submittedAt = now;
        order.executedAt = now;
        order.status = "EXECUTED";
        order.statusChangedAt = now;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    /**
     * 정산으로 실행된 매도다. 학생이 낸 주문이 아니라 교사가 다음 날로 넘길 때 서버가 만든다.
     */
    public static InvestmentOrder executedSell(
        UUID id,
        UUID classroomId,
        UUID studentId,
        UUID assetId,
        int quantity,
        long price,
        int priceStep,
        UUID transactionGroupId,
        Instant now
    ) {
        InvestmentOrder order = executedBuy(
            id, classroomId, studentId, assetId, quantity, price, priceStep, transactionGroupId, now
        );
        order.side = "SELL";
        return order;
    }

    public UUID getId() { return id; }
    public UUID getTransactionGroupId() { return transactionGroupId; }
    public String getStatus() { return status; }
    public int getQuantity() { return quantity; }
    public long getExecutedPrice() { return executedPrice; }
    public int getExecutedPriceStep() { return executedPriceStep; }
    public long getStateVersion() { return stateVersion; }
}
