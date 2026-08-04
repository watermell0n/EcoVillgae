package com.b201.ecovillage.realtime.infrastructure;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RealtimeConnectionJanitor {

    private final RealtimeEventBroadcaster broadcaster;
    private final Clock clock;

    public RealtimeConnectionJanitor(RealtimeEventBroadcaster broadcaster, Clock clock) {
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    public void closeExpiredConnections() {
        broadcaster.closeExpired(clock.instant());
    }
}
