package com.b201.ecovillage.realtime.application;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.realtime.config.RealtimeProperties;
import com.b201.ecovillage.realtime.domain.OutboxEvent;
import com.b201.ecovillage.realtime.domain.RealtimeStream;
import com.b201.ecovillage.realtime.repository.OutboxEventRepository;
import com.b201.ecovillage.realtime.repository.RealtimeStreamRepository;

@Service
public class OutboxDispatchWorker {

    private final OutboxEventRepository eventRepository;
    private final RealtimeStreamRepository streamRepository;
    private final RealtimeEventBus eventBus;
    private final RealtimeProperties properties;
    private final Clock clock;

    public OutboxDispatchWorker(
        OutboxEventRepository eventRepository,
        RealtimeStreamRepository streamRepository,
        RealtimeEventBus eventBus,
        RealtimeProperties properties,
        Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.streamRepository = streamRepository;
        this.eventBus = eventBus;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int dispatchPending() {
        List<OutboxEvent> events = eventRepository
            .findByPublishedAtIsNullOrderByOccurredAtAscEventIdAsc(
                PageRequest.of(0, properties.dispatchBatchSize())
            );
        for (OutboxEvent event : events) {
            try {
                RealtimeStream stream = streamRepository
                    .findById(event.getRealtimeStreamId())
                    .orElseThrow(() -> new IllegalStateException("Realtime stream is missing"));
                eventBus.publish(RealtimeEventEnvelope.from(event, stream));
                event.markPublished(clock.instant());
            } catch (RuntimeException exception) {
                event.markFailed(exception.getMessage());
            }
        }
        return events.size();
    }
}
