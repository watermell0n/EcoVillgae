package com.b201.ecovillage.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.realtime.domain.OutboxEvent;
import com.b201.ecovillage.realtime.domain.RealtimeStream;
import com.b201.ecovillage.realtime.repository.OutboxEventRepository;
import com.b201.ecovillage.realtime.repository.RealtimeStreamRepository;

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

    @Test
    void fansResourceEventOutToTeacherControlStream() {
        RealtimeStreamRepository streamRepository = mock(RealtimeStreamRepository.class);
        OutboxEventRepository eventRepository = mock(OutboxEventRepository.class);
        UuidV7Generator uuidGenerator = mock(UuidV7Generator.class);
        when(uuidGenerator.generate()).thenReturn(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        when(streamRepository.findByClassroomIdAndStreamTypeAndResourceId(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(streamRepository.findByClassroomIdAndStreamTypeAndResourceIdIsNull(any(), any()))
            .thenReturn(Optional.empty());
        when(streamRepository.saveAndFlush(any(RealtimeStream.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        RealtimeEventPublisher publisher = new RealtimeEventPublisher(
            streamRepository,
            eventRepository,
            uuidGenerator,
            Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC)
        );

        publisher.publish(
            UUID.randomUUID(),
            "ACTIVITY",
            UUID.randomUUID(),
            "activity.started",
            "ACTIVITY",
            UUID.randomUUID(),
            1,
            Map.of("phase", "LIVE"),
            true
        );

        ArgumentCaptor<RealtimeStream> streams = ArgumentCaptor.forClass(RealtimeStream.class);
        verify(streamRepository, times(4)).saveAndFlush(streams.capture());
        assertEquals(
            List.of("ACTIVITY", "ACTIVITY", "TEACHER_CONTROL", "TEACHER_CONTROL"),
            streams.getAllValues().stream().map(RealtimeStream::getStreamType).toList()
        );
        verify(eventRepository, times(2)).save(any(OutboxEvent.class));
    }
}
