package com.b201.ecovillage.realtime.infrastructure;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.realtime.application.StudentPresenceService;

@Component
public class StudentPresenceJanitor {

    private final StudentPresenceService presenceService;
    private final RealtimeEventBroadcaster broadcaster;
    private final Clock clock;

    public StudentPresenceJanitor(
        StudentPresenceService presenceService,
        RealtimeEventBroadcaster broadcaster,
        Clock clock
    ) {
        this.presenceService = presenceService;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 15_000)
    public void expireStaleConnections() {
        presenceService.expireStale(clock.instant()).forEach(broadcaster::broadcastPresence);
    }
}
