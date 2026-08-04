package com.b201.ecovillage.realtime.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.realtime.application.IssuedRealtimeTicket;
import com.b201.ecovillage.realtime.application.RealtimeTicket;

class InMemoryRealtimeTicketStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void consumesIssuedTicketOnlyOnce() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        InMemoryRealtimeTicketStore store = new InMemoryRealtimeTicketStore(
            new SecureRandom(),
            clock
        );
        RealtimeTicket expected = ticket(NOW.plusSeconds(60));

        IssuedRealtimeTicket issued = store.issue(expected);

        assertEquals(expected, store.consume(issued.ticket()).orElseThrow());
        assertTrue(store.consume(issued.ticket()).isEmpty());
    }

    @Test
    void rejectsExpiredTicketAndStillConsumesIt() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        InMemoryRealtimeTicketStore store = new InMemoryRealtimeTicketStore(
            new SecureRandom(),
            clock
        );
        IssuedRealtimeTicket issued = store.issue(ticket(NOW));

        assertTrue(store.consume(issued.ticket()).isEmpty());
        assertTrue(store.consume(issued.ticket()).isEmpty());
    }

    private static RealtimeTicket ticket(Instant expiresAt) {
        UUID classroomId = UUID.randomUUID();
        return new RealtimeTicket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "teacher-subject",
            UUID.randomUUID(),
            "TCH",
            classroomId,
            Set.of("WS-TCH-001"),
            Set.of(classroomId),
            expiresAt
        );
    }
}
