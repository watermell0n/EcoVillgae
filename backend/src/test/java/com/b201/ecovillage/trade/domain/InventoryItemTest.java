package com.b201.ecovillage.trade.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InventoryItemTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void locksAndReleasesItemForOneTrade() {
        UUID ownerId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        InventoryItem item = item(ownerId);

        item.lockForTrade(tradeId, ownerId, NOW.plusSeconds(1));
        assertEquals(InventoryItemStatus.LOCKED_TRADE, item.getStatus());
        assertEquals(tradeId, item.getLockedTradeId());

        item.releaseTradeLock(tradeId, NOW.plusSeconds(2));
        assertEquals(InventoryItemStatus.AVAILABLE, item.getStatus());
        assertNull(item.getLockedTradeId());
    }

    @Test
    void preventsWrongOwnerAndDoubleLock() {
        UUID ownerId = UUID.randomUUID();
        InventoryItem item = item(ownerId);

        assertThrows(
            TradeOperationException.class,
            () -> item.lockForTrade(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(1))
        );
        item.lockForTrade(UUID.randomUUID(), ownerId, NOW.plusSeconds(2));
        assertThrows(
            TradeOperationException.class,
            () -> item.lockForTrade(UUID.randomUUID(), ownerId, NOW.plusSeconds(3))
        );
    }

    private static InventoryItem item(UUID ownerId) {
        return InventoryItem.acquire(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ownerId,
            UUID.randomUUID(),
            null,
            NOW
        );
    }
}
