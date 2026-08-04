package com.b201.ecovillage.realtime.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.realtime.application.RealtimeEventBus;
import com.b201.ecovillage.realtime.application.RealtimeEventEnvelope;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
    prefix = "ecovillage.realtime",
    name = "redis-enabled",
    havingValue = "true"
)
public class RedisRealtimeEventBus implements RealtimeEventBus {

    public static final String TOPIC = "ecovillage:realtime:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisRealtimeEventBus(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(RealtimeEventEnvelope event) {
        try {
            redis.convertAndSend(TOPIC, objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish realtime event", exception);
        }
    }
}
