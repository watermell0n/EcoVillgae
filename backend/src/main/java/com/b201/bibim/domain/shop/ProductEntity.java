package com.b201.bibim.domain.shop;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "original_price", nullable = false)
    private long originalPrice;

    @Column(name = "current_price", nullable = false)
    private long currentPrice;

    @Column(name = "initial_stock", nullable = false)
    private int initialStock;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {
    }

    public void sell(int quantity) {
        if (quantity <= 0 || currentStock < quantity || !"ON_SALE".equals(status)) {
            throw new IllegalStateException("판매할 수 없는 상품입니다.");
        }
        currentStock -= quantity;
        if (currentStock == 0) {
            status = "SOLD_OUT";
        }
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getOriginalPrice() {
        return originalPrice;
    }

    public long getCurrentPrice() {
        return currentPrice;
    }

    public int getInitialStock() {
        return initialStock;
    }

    public int getCurrentStock() {
        return currentStock;
    }

    public String getStatus() {
        return status;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
