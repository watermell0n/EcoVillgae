package com.b201.ecovillage.character.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.domain.InventoryItemStatus;
import com.b201.ecovillage.trade.domain.TradeOperationException;

class InventoryEquipmentTest {

    private static final Instant ACQUIRED_AT = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void ownedItemCanBeEquippedAndUnequipped() {
        UUID studentId = UUID.randomUUID();
        InventoryItem item = item(studentId);

        item.equip(studentId, ACQUIRED_AT.plusSeconds(1));
        assertEquals(InventoryItemStatus.EQUIPPED, item.getStatus());
        assertEquals(ACQUIRED_AT.plusSeconds(1), item.getEquippedAt());

        item.unequip(studentId, ACQUIRED_AT.plusSeconds(2));
        assertEquals(InventoryItemStatus.AVAILABLE, item.getStatus());
        assertNull(item.getEquippedAt());
    }

    @Test
    void anotherStudentCannotEquipTheItem() {
        InventoryItem item = item(UUID.randomUUID());

        TradeOperationException exception = assertThrows(
            TradeOperationException.class,
            () -> item.equip(UUID.randomUUID(), ACQUIRED_AT.plusSeconds(1))
        );
        assertEquals("RESOURCE_NOT_OWNED", exception.getCode());
    }

    private static InventoryItem item(UUID studentId) {
        return InventoryItem.acquire(
            UUID.randomUUID(),
            UUID.randomUUID(),
            studentId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            ACQUIRED_AT
        );
    }
}
