package com.b201.bibim.domain.character;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "character_cosmetics")
public class CharacterCosmeticEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String slot;

    @Column(nullable = false)
    private long price;

    @Column(name = "sprite_key", nullable = false, length = 100)
    private String spriteKey;

    @Column(nullable = false, length = 20)
    private String theme;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> anchor;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CharacterCosmeticEntity() {
    }

    public boolean isOnSale() {
        return "ON_SALE".equals(status);
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

    public String getSlot() {
        return slot;
    }

    public long getPrice() {
        return price;
    }

    public String getSpriteKey() {
        return spriteKey;
    }

    public String getTheme() {
        return theme;
    }

    public Map<String, Object> getAnchor() {
        return anchor == null ? Map.of() : Map.copyOf(anchor);
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getStatus() {
        return status;
    }
}
