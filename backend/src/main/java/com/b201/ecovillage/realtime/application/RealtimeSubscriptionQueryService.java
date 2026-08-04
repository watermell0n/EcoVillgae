package com.b201.ecovillage.realtime.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.realtime.domain.OutboxEvent;
import com.b201.ecovillage.realtime.domain.RealtimeStream;
import com.b201.ecovillage.realtime.repository.OutboxEventRepository;
import com.b201.ecovillage.realtime.repository.RealtimeStreamRepository;

@Service
public class RealtimeSubscriptionQueryService {

    private final RealtimeStreamRepository streamRepository;
    private final OutboxEventRepository eventRepository;

    public RealtimeSubscriptionQueryService(
        RealtimeStreamRepository streamRepository,
        OutboxEventRepository eventRepository
    ) {
        this.streamRepository = streamRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public RealtimeSubscriptionSnapshot load(
        RealtimeTicket ticket,
        RealtimeChannel channel,
        UUID requestedResourceId,
        long lastSequence
    ) {
        if (lastSequence < 0) {
            throw new RealtimeProtocolException(
                "INVALID_LAST_SEQUENCE",
                "last_sequence must not be negative"
            );
        }
        UUID streamResourceId = streamResourceId(ticket, channel, requestedResourceId);
        RealtimeStream stream = streamResourceId == null
            ? streamRepository
                .findForDeliveryWithoutResource(ticket.classroomId(), channel.streamType())
                .orElse(null)
            : streamRepository
                .findForDelivery(ticket.classroomId(), channel.streamType(), streamResourceId)
                .orElse(null);
        if (stream == null) {
            return new RealtimeSubscriptionSnapshot(
                0,
                1,
                List.of(),
                false,
                snapshotUrl(ticket.classroomId(), channel, requestedResourceId)
            );
        }

        long currentSequence = stream.getCurrentSequence();
        long retentionFrom = stream.getRetentionFromSequence();
        boolean outsideRetention = lastSequence < retentionFrom - 1;
        if (outsideRetention) {
            return new RealtimeSubscriptionSnapshot(
                currentSequence,
                retentionFrom,
                List.of(),
                true,
                snapshotUrl(ticket.classroomId(), channel, requestedResourceId)
            );
        }

        List<RealtimeEventEnvelope> events = eventRepository
            .findTop500ByRealtimeStreamIdAndChannelSequenceGreaterThanOrderByChannelSequenceAsc(
                stream.getId(),
                lastSequence
            )
            .stream()
            .map(event -> RealtimeEventEnvelope.from(event, stream))
            .toList();
        boolean truncated = currentSequence - lastSequence > events.size();
        return new RealtimeSubscriptionSnapshot(
            currentSequence,
            retentionFrom,
            truncated ? List.of() : events,
            truncated,
            snapshotUrl(ticket.classroomId(), channel, requestedResourceId)
        );
    }

    public static UUID streamResourceId(
        RealtimeTicket ticket,
        RealtimeChannel channel,
        UUID requestedResourceId
    ) {
        return switch (channel.scope()) {
            case CLASSROOM -> null;
            case SELF -> ticket.actorId();
            case RESOURCE -> requestedResourceId;
        };
    }

    private static String snapshotUrl(
        UUID classroomId,
        RealtimeChannel channel,
        UUID resourceId
    ) {
        return switch (channel) {
            case CLASSROOM, ECONOMY -> "/classrooms/" + classroomId + "/modules-and-policies";
            case STUDENT_PRIVATE -> "/classrooms/" + classroomId
                + "/students/me/deposit-and-savings";
            case TEACHER_CONTROL -> "/classrooms/" + classroomId + "/audit-events";
            case ACTIVITY -> "/classrooms/" + classroomId + "/guided-activities/" + resourceId;
            case QUIZ -> "/classrooms/" + classroomId + "/quiz-sessions/" + resourceId;
            case VOTE -> "/classrooms/" + classroomId + "/votes/" + resourceId;
            case INVESTMENT -> "/classrooms/" + classroomId + "/investment-market";
            case OPERATION -> "/operations/" + resourceId;
            case DISPLAY -> null;
        };
    }
}
