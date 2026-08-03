package com.b201.ecovillage.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.realtime.config.RealtimeProperties;
import com.b201.ecovillage.realtime.domain.OutboxEvent;
import com.b201.ecovillage.realtime.domain.RealtimeStream;
import com.b201.ecovillage.realtime.repository.OutboxEventRepository;
import com.b201.ecovillage.realtime.repository.RealtimeStreamRepository;

class OutboxDispatchWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void publishesPendingEventAndMarksItPublished() {
        OutboxEventRepository eventRepository = mock(OutboxEventRepository.class);
        RealtimeStreamRepository streamRepository = mock(RealtimeStreamRepository.class);
        RealtimeEventBus eventBus = mock(RealtimeEventBus.class);
        RealtimeStream stream = RealtimeStream.active(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ECONOMY",
            null,
            NOW
        );
        OutboxEvent event = OutboxEvent.pending(
            UUID.randomUUID(),
            stream.getClassroomId(),
            stream.getId(),
            1,
            "economy.changed",
            "ECONOMY",
            UUID.randomUUID(),
            1,
            Map.of(),
            NOW
        );
        when(eventRepository.findByPublishedAtIsNullOrderByOccurredAtAscEventIdAsc(any()))
            .thenReturn(List.of(event));
        when(streamRepository.findById(stream.getId())).thenReturn(Optional.of(stream));
        OutboxDispatchWorker worker = worker(eventRepository, streamRepository, eventBus);

        assertEquals(1, worker.dispatchPending());

        verify(eventBus).publish(any(RealtimeEventEnvelope.class));
    }

    private static OutboxDispatchWorker worker(
        OutboxEventRepository eventRepository,
        RealtimeStreamRepository streamRepository,
        RealtimeEventBus eventBus
    ) {
        return new OutboxDispatchWorker(
            eventRepository,
            streamRepository,
            eventBus,
            new RealtimeProperties(
                false,
                Duration.ofSeconds(60),
                Duration.ofHours(8),
                Duration.ofSeconds(25),
                100,
                250
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
