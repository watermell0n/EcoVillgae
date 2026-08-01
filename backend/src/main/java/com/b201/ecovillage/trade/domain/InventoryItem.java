package com.b201.ecovillage.trade.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false)
    private UUID classroomStudentId;

    @Column(name = "character_catalog_id", nullable = false, updatable = false)
    private UUID characterCatalogId;

    @Column(name = "source_order_item_id", updatable = false)
    private UUID sourceOrderItemId;

    @Column(name = "locked_trade_id")
    private UUID lockedTradeId;

    @Column(name = "equipped_at")
    private Instant equippedAt;

    @Column(name = "acquired_at", nullable = false, updatable = false)
    private Instant acquiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InventoryItemStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    private InventoryItem(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID characterCatalogId,
        UUID sourceOrderItemId,
        Instant acquiredAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.characterCatalogId = Objects.requireNonNull(
            characterCatalogId,
            "characterCatalogId must not be null"
        );
        this.sourceOrderItemId = sourceOrderItemId;
        this.acquiredAt = Objects.requireNonNull(acquiredAt, "acquiredAt must not be null");
        this.status = InventoryItemStatus.AVAILABLE;
        this.statusChangedAt = acquiredAt;
        this.createdAt = acquiredAt;
        this.updatedAt = acquiredAt;
    }

    public static InventoryItem acquire(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID characterCatalogId,
        UUID sourceOrderItemId,
        Instant acquiredAt
    ) {
        return new InventoryItem(
            id,
            classroomId,
            classroomStudentId,
            characterCatalogId,
            sourceOrderItemId,
            acquiredAt
        );
    }

    public void lockForTrade(UUID tradeId, UUID expectedOwnerId, Instant changedAt) {
        if (!classroomStudentId.equals(expectedOwnerId)) {
            throw new TradeOperationException(
                "RESOURCE_NOT_OWNED",
                "거래에 등록할 아이템의 소유자가 아닙니다."
            );
        }
        if (status != InventoryItemStatus.AVAILABLE) {
            throw new TradeOperationException(
                "ITEM_LOCKED",
                "장착 중이거나 다른 거래에 잠긴 아이템입니다."
            );
        }
        lockedTradeId = Objects.requireNonNull(tradeId, "tradeId must not be null");
        changeStatus(InventoryItemStatus.LOCKED_TRADE, changedAt);
    }

    public void releaseTradeLock(UUID tradeId, Instant changedAt) {
        if (
            status != InventoryItemStatus.LOCKED_TRADE
                || !Objects.equals(lockedTradeId, tradeId)
        ) {
            throw new TradeOperationException(
                "OWNERSHIP_CHANGED",
                "거래 아이템 잠금 상태가 변경되었습니다."
            );
        }
        lockedTradeId = null;
        changeStatus(InventoryItemStatus.AVAILABLE, changedAt);
    }

    public void transferTo(UUID tradeId, UUID nextOwnerId, Instant changedAt) {
        if (
            status != InventoryItemStatus.LOCKED_TRADE
                || !Objects.equals(lockedTradeId, tradeId)
        ) {
            throw new TradeOperationException(
                "OWNERSHIP_CHANGED",
                "거래 아이템 잠금 상태가 변경되었습니다."
            );
        }
        classroomStudentId = Objects.requireNonNull(nextOwnerId, "nextOwnerId must not be null");
        lockedTradeId = null;
        changeStatus(InventoryItemStatus.AVAILABLE, changedAt);
    }

    public void equip(UUID expectedOwnerId, Instant changedAt) {
        requireOwner(expectedOwnerId);
        if (status != InventoryItemStatus.AVAILABLE) {
            throw new TradeOperationException(
                "INVALID_STATE_TRANSITION",
                "보유 중인 아이템만 장착할 수 있습니다."
            );
        }
        equippedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        changeStatus(InventoryItemStatus.EQUIPPED, changedAt);
    }

    public void unequip(UUID expectedOwnerId, Instant changedAt) {
        requireOwner(expectedOwnerId);
        if (status != InventoryItemStatus.EQUIPPED) {
            throw new TradeOperationException(
                "INVALID_STATE_TRANSITION",
                "장착 중인 아이템만 해제할 수 있습니다."
            );
        }
        equippedAt = null;
        changeStatus(InventoryItemStatus.AVAILABLE, changedAt);
    }

    private void requireOwner(UUID expectedOwnerId) {
        if (!classroomStudentId.equals(expectedOwnerId)) {
            throw new TradeOperationException(
                "RESOURCE_NOT_OWNED",
                "본인이 보유한 아이템만 변경할 수 있습니다."
            );
        }
    }

    private void changeStatus(InventoryItemStatus next, Instant changedAt) {
        status = next;
        statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public UUID getCharacterCatalogId() {
        return characterCatalogId;
    }

    public UUID getSourceOrderItemId() {
        return sourceOrderItemId;
    }

    public UUID getLockedTradeId() {
        return lockedTradeId;
    }

    public InventoryItemStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getEquippedAt() {
        return equippedAt;
    }
}
