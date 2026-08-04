package com.b201.ecovillage.realtime.infrastructure;

import java.util.UUID;

import com.b201.ecovillage.realtime.application.RealtimeChannel;
import com.b201.ecovillage.realtime.application.RealtimeEventEnvelope;
import com.b201.ecovillage.realtime.application.RealtimeTicket;

final class RealtimeSubscription {

    private final UUID id;
    private final RealtimeChannel channel;
    private final UUID resourceId;
    private long lastDeliveredSequence;
    private long lastAcknowledgedSequence;

    RealtimeSubscription(
        UUID id,
        RealtimeChannel channel,
        UUID resourceId,
        long lastDeliveredSequence
    ) {
        this.id = id;
        this.channel = channel;
        this.resourceId = resourceId;
        this.lastDeliveredSequence = lastDeliveredSequence;
        this.lastAcknowledgedSequence = lastDeliveredSequence;
    }

    UUID id() {
        return id;
    }

    RealtimeChannel channel() {
        return channel;
    }

    UUID resourceId() {
        return resourceId;
    }

    long lastDeliveredSequence() {
        return lastDeliveredSequence;
    }

    void delivered(long sequence) {
        lastDeliveredSequence = Math.max(lastDeliveredSequence, sequence);
    }

    void acknowledge(long sequence) {
        if (sequence > lastDeliveredSequence) {
            throw new IllegalArgumentException("Cannot acknowledge an undelivered sequence");
        }
        lastAcknowledgedSequence = Math.max(lastAcknowledgedSequence, sequence);
    }

    boolean matches(RealtimeTicket ticket, RealtimeEventEnvelope event) {
        if (!ticket.classroomId().equals(event.classroomId())) {
            return false;
        }
        if (!channel.streamType().equals(event.streamType())) {
            return false;
        }
        UUID expectedResource = switch (channel.scope()) {
            case CLASSROOM -> null;
            case SELF -> ticket.actorId();
            case RESOURCE -> resourceId;
        };
        return expectedResource == null
            ? event.resourceId() == null
            : expectedResource.equals(event.resourceId());
    }
}
