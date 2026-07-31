package com.b201.ecovillage.common.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

public class UuidV7Generator {

    private final Clock clock;
    private final SecureRandom secureRandom;

    public UuidV7Generator(Clock clock, SecureRandom secureRandom) {
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public UUID generate() {
        long unixMillis = clock.millis() & 0x0000FFFFFFFFFFFFL;
        long randomHigh = secureRandom.nextLong();
        long randomLow = secureRandom.nextLong();

        long mostSignificantBits =
            (unixMillis << 16)
                | 0x7000L
                | (randomHigh & 0x0FFFL);
        long leastSignificantBits =
            (randomLow & 0x3FFFFFFFFFFFFFFFL)
                | 0x8000000000000000L;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
