package com.b201.ecovillage.realtime.infrastructure;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.realtime.application.IssuedRealtimeTicket;
import com.b201.ecovillage.realtime.application.RealtimeTicket;
import com.b201.ecovillage.realtime.application.RealtimeTicketStore;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
    prefix = "ecovillage.realtime",
    name = "redis-enabled",
    havingValue = "true"
)
public class RedisRealtimeTicketStore implements RealtimeTicketStore {

    private static final String KEY_PREFIX = "ecovillage:realtime:ticket:";
    private static final DefaultRedisScript<String> CONSUME_SCRIPT =
        new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
              redis.call('DEL', KEYS[1])
            end
            return value
            """, String.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public RedisRealtimeTicketStore(
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        SecureRandom secureRandom,
        Clock clock
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Override
    public IssuedRealtimeTicket issue(RealtimeTicket ticket) {
        try {
            String secret = RealtimeTicketSecrets.newSecret(secureRandom);
            Duration ttl = Duration.between(clock.instant(), ticket.expiresAt());
            redis.opsForValue().set(
                key(secret),
                objectMapper.writeValueAsString(ticket),
                ttl
            );
            return new IssuedRealtimeTicket(secret, ticket.expiresAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store realtime ticket", exception);
        }
    }

    @Override
    public Optional<RealtimeTicket> consume(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = redis.execute(CONSUME_SCRIPT, List.of(key(rawTicket)));
            if (json == null) {
                return Optional.empty();
            }
            RealtimeTicket ticket = objectMapper.readValue(json, RealtimeTicket.class);
            return ticket.isExpiredAt(clock.instant()) ? Optional.empty() : Optional.of(ticket);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to consume realtime ticket", exception);
        }
    }

    private static String key(String rawTicket) {
        return KEY_PREFIX + RealtimeTicketSecrets.hash(rawTicket);
    }
}
