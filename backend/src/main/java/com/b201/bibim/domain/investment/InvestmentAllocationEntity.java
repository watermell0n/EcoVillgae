package com.b201.bibim.domain.investment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "investment_allocations")
public class InvestmentAllocationEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvestmentAllocationEntity() {
    }

    public static InvestmentAllocationEntity create(UUID orderId, UUID assetId, long amount) {
        InvestmentAllocationEntity allocation = new InvestmentAllocationEntity();
        allocation.id = UUID.randomUUID();
        allocation.orderId = orderId;
        allocation.assetId = assetId;
        allocation.amount = amount;
        allocation.createdAt = Instant.now();
        return allocation;
    }

    public UUID getAssetId() { return assetId; }
    public long getAmount() { return amount; }
}
