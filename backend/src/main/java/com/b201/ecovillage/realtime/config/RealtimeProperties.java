package com.b201.ecovillage.realtime.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecovillage.realtime")
public record RealtimeProperties(
    boolean redisEnabled,
    Duration ticketTtl,
    Duration connectionTtl,
    Duration heartbeatInterval,
    int dispatchBatchSize,
    long dispatchDelayMs
) {

    public RealtimeProperties {
        requirePositive(ticketTtl, "ticketTtl");
        requirePositive(connectionTtl, "connectionTtl");
        requirePositive(heartbeatInterval, "heartbeatInterval");
        if (dispatchBatchSize < 1 || dispatchBatchSize > 1000) {
            throw new IllegalArgumentException("dispatchBatchSize must be between 1 and 1000");
        }
        if (dispatchDelayMs < 10) {
            throw new IllegalArgumentException("dispatchDelayMs must be at least 10");
        }
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
