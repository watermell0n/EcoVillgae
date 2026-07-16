package com.b201.bibim.domain.investment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "investment_assets")
public class InvestmentAssetEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "min_return_bps", nullable = false)
    private int minReturnBps;

    @Column(name = "max_return_bps", nullable = false)
    private int maxReturnBps;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvestmentAssetEntity() {
    }

    public static InvestmentAssetEntity create(
            UUID roomId,
            String code,
            String name,
            String description,
            String riskLevel,
            int minReturnBps,
            int maxReturnBps,
            int displayOrder
    ) {
        InvestmentAssetEntity asset = new InvestmentAssetEntity();
        asset.id = UUID.randomUUID();
        asset.roomId = roomId;
        asset.code = code;
        asset.name = name;
        asset.description = description;
        asset.riskLevel = riskLevel;
        asset.minReturnBps = minReturnBps;
        asset.maxReturnBps = maxReturnBps;
        asset.displayOrder = displayOrder;
        asset.createdAt = Instant.now();
        return asset;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRiskLevel() { return riskLevel; }
    public int getMinReturnBps() { return minReturnBps; }
    public int getMaxReturnBps() { return maxReturnBps; }
    public int getDisplayOrder() { return displayOrder; }
}
