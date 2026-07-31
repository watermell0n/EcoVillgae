package com.b201.ecovillage.realtime.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.realtime.domain.OutboxEvent;
import com.b201.ecovillage.realtime.domain.RealtimeStream;
import com.b201.ecovillage.realtime.repository.OutboxEventRepository;
import com.b201.ecovillage.realtime.repository.RealtimeStreamRepository;

@Service
public class RealtimeEventPublisher {

    private static final Set<String> FORBIDDEN_PUBLIC_KEYS = Set.of(
        "student_id",
        "student_ids",
        "classroom_student_id",
        "nickname",
        "roster_number",
        "balance",
        "answer",
        "correct_answer",
        "score",
        "credit_level"
    );

    private final RealtimeStreamRepository streamRepository;
    private final OutboxEventRepository eventRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public RealtimeEventPublisher(
        RealtimeStreamRepository streamRepository,
        OutboxEventRepository eventRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.streamRepository = streamRepository;
        this.eventRepository = eventRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public UUID publish(
        UUID classroomId,
        String streamType,
        UUID resourceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        long stateVersion,
        Map<String, Object> payload,
        boolean publicSafe
    ) {
        if (publicSafe) {
            requirePublicSafe(payload);
        }
        Instant now = clock.instant();
        RealtimeStream stream = findStream(classroomId, streamType, resourceId);
        if (stream == null) {
            stream = streamRepository.saveAndFlush(
                RealtimeStream.active(
                    uuidGenerator.generate(),
                    classroomId,
                    streamType,
                    resourceId,
                    now
                )
            );
        }
        long sequence = stream.reserveNextSequence(now);
        streamRepository.saveAndFlush(stream);
        OutboxEvent event = eventRepository.save(
            OutboxEvent.pending(
                uuidGenerator.generate(),
                classroomId,
                stream.getId(),
                sequence,
                eventType,
                aggregateType,
                aggregateId,
                stateVersion,
                payload,
                now
            )
        );
        return event.getEventId();
    }

    private RealtimeStream findStream(
        UUID classroomId,
        String streamType,
        UUID resourceId
    ) {
        return resourceId == null
            ? streamRepository
                .findByClassroomIdAndStreamTypeAndResourceIdIsNull(
                    classroomId,
                    streamType
                )
                .orElse(null)
            : streamRepository
                .findByClassroomIdAndStreamTypeAndResourceId(
                    classroomId,
                    streamType,
                    resourceId
                )
                .orElse(null);
    }

    public static void requirePublicSafe(Map<String, Object> payload) {
        inspect(payload);
    }

    private static void inspect(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                String normalized = key.toString().toLowerCase();
                if (FORBIDDEN_PUBLIC_KEYS.contains(normalized)) {
                    throw new IllegalArgumentException(
                        "Public event payload contains private field: " + key
                    );
                }
                inspect(nested);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(RealtimeEventPublisher::inspect);
        }
    }
}
