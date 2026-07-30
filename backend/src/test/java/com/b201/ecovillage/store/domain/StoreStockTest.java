package com.b201.ecovillage.store.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StoreStockTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void reservesConsumesAndReleasesWithoutNegativeStock() {
        StoreStock stock = StoreStock.initialize(
            UUID.randomUUID(),
            UUID.randomUUID(),
            10,
            NOW
        );

        stock.reserve(4, NOW.plusSeconds(1));
        assertEquals(6, stock.getAvailableQuantity());
        assertEquals(4, stock.getReservedQuantity());

        stock.consumeReserved(3, NOW.plusSeconds(2));
        stock.releaseReserved(1, NOW.plusSeconds(3));
        assertEquals(7, stock.getAvailableQuantity());
        assertEquals(0, stock.getReservedQuantity());
    }

    @Test
    void rejectsReservationBeyondAvailableQuantity() {
        StoreStock stock = StoreStock.initialize(
            UUID.randomUUID(),
            UUID.randomUUID(),
            2,
            NOW
        );

        assertThrows(
            InsufficientStoreStockException.class,
            () -> stock.reserve(3, NOW)
        );
        assertEquals(2, stock.getAvailableQuantity());
    }
}
