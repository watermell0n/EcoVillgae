package com.b201.ecovillage.realtime.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RealtimeEventPublisherTest {

    @Test
    void rejectsPersonalFieldsFromPublicPayload() {
        assertThrows(
            IllegalArgumentException.class,
            () -> RealtimeEventPublisher.requirePublicSafe(
                Map.of(
                    "anonymous_counts", Map.of("joined", 20),
                    "student_id", "private"
                )
            )
        );
    }
}
