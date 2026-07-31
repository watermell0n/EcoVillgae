package com.b201.ecovillage.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    @Test
    void capturesImmutableBalanceEvidence() {
        LedgerEntry entry = LedgerEntry.post(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new LedgerAccountPosting(3, -25, 100, 75),
            Instant.parse("2026-07-30T00:00:00Z")
        );

        assertEquals(3, entry.getAccountSequence());
        assertEquals(-25, entry.getAmount());
        assertEquals(100, entry.getBalanceBefore());
        assertEquals(75, entry.getBalanceAfter());
    }

    @Test
    void rejectsInconsistentSnapshots() {
        assertThrows(
            IllegalArgumentException.class,
            () -> LedgerEntry.post(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new LedgerAccountPosting(1, 10, 0, 9),
                Instant.parse("2026-07-30T00:00:00Z")
            )
        );
    }
}
