package com.b201.bibim.domain.settlement;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_settlement_allocations")
public class FinancialSettlementAllocationEntity {

    @Id
    private UUID id;

    @Column(name = "settlement_item_id", nullable = false)
    private UUID settlementItemId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private long principal;

    @Column(name = "return_bps", nullable = false)
    private int returnBps;

    @Column(nullable = false)
    private long profit;

    @Column(name = "final_value", nullable = false)
    private long finalValue;

    protected FinancialSettlementAllocationEntity() {
    }

    public static FinancialSettlementAllocationEntity create(
            UUID settlementItemId,
            UUID assetId,
            long principal,
            int returnBps,
            long profit,
            long finalValue
    ) {
        FinancialSettlementAllocationEntity allocation =
                new FinancialSettlementAllocationEntity();
        allocation.id = UUID.randomUUID();
        allocation.settlementItemId = settlementItemId;
        allocation.assetId = assetId;
        allocation.principal = principal;
        allocation.returnBps = returnBps;
        allocation.profit = profit;
        allocation.finalValue = finalValue;
        return allocation;
    }

    public UUID getAssetId() { return assetId; }
    public long getPrincipal() { return principal; }
    public int getReturnBps() { return returnBps; }
    public long getProfit() { return profit; }
    public long getFinalValue() { return finalValue; }
}
