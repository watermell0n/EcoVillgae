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

    @Column(name = "image_url", columnDefinition = "TEXT")
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

    public static ProductEntity forSale(
            UUID roomId,
            String name,
            String description,
            long price,
            int stock,
            int displayOrder
    ) {
        ProductEntity product = new ProductEntity();
        product.id = UUID.randomUUID();
        product.roomId = roomId;
        product.name = name;
        product.description = description;
        product.originalPrice = price;
        product.currentPrice = price;
        product.initialStock = stock;
        product.currentStock = stock;
        product.status = stock == 0 ? "SOLD_OUT" : "ON_SALE";
        product.displayOrder = displayOrder;
        product.updatedAt = Instant.now();
        return product;
    }

    public static ProductEntity forSale(
            UUID roomId,
            String name,
            String description,
            long price,
            int stock,
            int displayOrder,
            String imageUrl
    ) {
        ProductEntity product = forSale(roomId, name, description, price, stock, displayOrder);
        product.imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
        return product;
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

    public void updateCurrentPrice(long price) {
        if (price < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다.");
        }
        currentPrice = price;
        updatedAt = Instant.now();
    }

    public void updateCatalog(String name, String description, long price, String imageUrl) {
        if (price < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다.");
        }
        this.name = name;
        this.description = description;
        this.originalPrice = price;
        this.currentPrice = price;
        this.imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
        this.updatedAt = Instant.now();
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
