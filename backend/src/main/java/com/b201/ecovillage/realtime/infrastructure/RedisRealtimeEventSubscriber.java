package com.b201.ecovillage.realtime.infrastructure;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.realtime.application.RealtimeEventEnvelope;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
    prefix = "ecovillage.realtime",
    name = "redis-enabled",
    havingValue = "true"
)
public class RedisRealtimeEventSubscriber implements MessageListener {

    private final RealtimeEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public RedisRealtimeEventSubscriber(
        RealtimeEventBroadcaster broadcaster,
        ObjectMapper objectMapper
    ) {
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeEventEnvelope event = objectMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8),
                RealtimeEventEnvelope.class
            );
            broadcaster.broadcast(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to consume realtime event", exception);
        }
    }
}
