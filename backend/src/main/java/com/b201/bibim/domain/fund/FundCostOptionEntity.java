package com.b201.bibim.domain.fund;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fund_cost_options")
public class FundCostOptionEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long amount;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected FundCostOptionEntity() {
    }

    public static FundCostOptionEntity create(UUID roomId, String name, long amount, int displayOrder) {
        FundCostOptionEntity option = new FundCostOptionEntity();
        option.id = UUID.randomUUID();
        option.roomId = roomId;
        option.name = name;
        option.amount = amount;
        option.enabled = true;
        option.displayOrder = displayOrder;
        return option;
    }

    public void update(String name, long amount, boolean enabled) {
        this.name = name.trim();
        this.amount = amount;
        this.enabled = enabled;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public String getName() { return name; }
    public long getAmount() { return amount; }
    public boolean isEnabled() { return enabled; }
    public int getDisplayOrder() { return displayOrder; }
}

