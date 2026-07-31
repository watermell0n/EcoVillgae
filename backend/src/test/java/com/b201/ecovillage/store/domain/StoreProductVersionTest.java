package com.b201.ecovillage.store.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StoreProductVersionTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void isPurchasableOnlyDuringPublishedLifetime() {
        StoreProductVersion version = StoreProductVersion.publish(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            StoreProductKind.DIGITAL,
            500,
            1_000,
            true,
            Map.of(),
            NOW
        );

        assertTrue(version.isPurchasableAt(NOW));
        version.retire(NOW.plusSeconds(10));
        assertTrue(version.isPurchasableAt(NOW.plusSeconds(9)));
        assertFalse(version.isPurchasableAt(NOW.plusSeconds(10)));
    }

    @Test
    void rejectsInvalidVatRate() {
        assertThrows(
            IllegalArgumentException.class,
            () -> StoreProductVersion.publish(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                StoreProductKind.PHYSICAL,
                100,
                10_001,
                true,
                Map.of(),
                NOW
            )
        );
    }
}
