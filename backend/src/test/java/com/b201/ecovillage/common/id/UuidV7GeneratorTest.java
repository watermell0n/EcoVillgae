package com.b201.ecovillage.common.id;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void createsRfc4122VariantUuidVersionSeven() {
        UuidV7Generator generator = new UuidV7Generator(
            Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC),
            new SecureRandom()
        );

        UUID value = generator.generate();

        assertEquals(7, value.version());
        assertEquals(2, value.variant());
        assertEquals(
            Instant.parse("2026-07-30T00:00:00Z").toEpochMilli(),
            value.getMostSignificantBits() >>> 16
        );
    }
}
