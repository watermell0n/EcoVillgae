package com.b201.ecovillage.realtime.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.b201.ecovillage.realtime.domain.OutboxEvent;
import com.b201.ecovillage.realtime.domain.RealtimeStream;

public record RealtimeEventEnvelope(
    UUID eventId,
    UUID classroomId,
    UUID streamId,
    String streamType,
    UUID resourceId,
    long sequence,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    long stateVersion,
    Instant occurredAt,
    Map<String, Object> payload
) {

    public RealtimeEventEnvelope {
        payload = Map.copyOf(payload);
    }

    public static RealtimeEventEnvelope from(
        OutboxEvent event,
        RealtimeStream stream
    ) {
        return new RealtimeEventEnvelope(
            event.getEventId(),
            event.getClassroomId(),
            stream.getId(),
            stream.getStreamType(),
            stream.getResourceId(),
            event.getChannelSequence(),
            event.getEventType(),
            event.getAggregateType(),
            event.getAggregateId(),
            event.getStateVersion(),
            event.getOccurredAt(),
            event.getPayload()
        );
    }
}
