package com.b201.ecovillage.realtime.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.realtime.application.RealtimeEventBus;
import com.b201.ecovillage.realtime.application.RealtimeEventEnvelope;

@Component
@ConditionalOnProperty(
    prefix = "ecovillage.realtime",
    name = "redis-enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class LocalRealtimeEventBus implements RealtimeEventBus {

    private final RealtimeEventBroadcaster broadcaster;

    public LocalRealtimeEventBus(RealtimeEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void publish(RealtimeEventEnvelope event) {
        broadcaster.broadcast(event);
    }
}
