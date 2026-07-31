package com.b201.ecovillage.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import com.b201.ecovillage.identity.application.TeacherSessionRecord;
import com.b201.ecovillage.identity.application.TeacherSessionStatus;
import com.b201.ecovillage.identity.application.TeacherSessionStore;

@Repository
public class RedisTeacherSessionStore implements TeacherSessionStore {

    private static final String SESSION_PREFIX = "auth:teacher-session:v1:";
    private static final String REFRESH_PREFIX = "auth:teacher-refresh:v1:";

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
        new DefaultRedisScript<>("""
            local stored = redis.call('HGET', KEYS[1], 'refresh_hash')
            local status = redis.call('HGET', KEYS[1], 'status')
            if not stored or stored ~= ARGV[1] or status ~= 'ACTIVE' then
              return 0
            end
            redis.call('DEL', KEYS[2])
            redis.call('HSET', KEYS[1],
              'refresh_hash', ARGV[2],
              'expires_at', ARGV[3],
              'updated_at', ARGV[4])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
            redis.call('SET', KEYS[3], ARGV[6], 'EX', tonumber(ARGV[5]))
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT =
        new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            local refresh_hash = redis.call('HGET', KEYS[1], 'refresh_hash')
            if refresh_hash then
              redis.call('DEL', ARGV[1] .. refresh_hash)
            end
            redis.call('HSET', KEYS[1],
              'status', 'REVOKED',
              'revoked_at', ARGV[2],
              'updated_at', ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisTeacherSessionStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void save(TeacherSessionRecord session) {
        Duration ttl = positiveTtl(session.expiresAt());
        String sessionKey = sessionKey(session.sessionId());
        Map<String, String> values = new HashMap<>();
        values.put("session_id", session.sessionId().toString());
        values.put("user_id", session.userId().toString());
        values.put("external_subject", session.externalSubject());
        values.put("refresh_hash", session.refreshTokenHash());
        values.put("status", session.status().name());
        values.put("issued_at", session.issuedAt().toString());
        values.put("expires_at", session.expiresAt().toString());
        values.put("updated_at", session.issuedAt().toString());
        redis.opsForHash().putAll(sessionKey, values);
        redis.expire(sessionKey, ttl);
        redis.opsForValue().set(
            refreshKey(session.refreshTokenHash()),
            session.sessionId().toString(),
            ttl
        );
    }

    @Override
    public Optional<TeacherSessionRecord> findBySessionId(UUID sessionId) {
        Map<Object, Object> values = redis.opsForHash().entries(sessionKey(sessionId));
        return values.isEmpty() ? Optional.empty() : Optional.of(toRecord(values));
    }

    @Override
    public Optional<TeacherSessionRecord> findByRefreshTokenHash(String refreshTokenHash) {
        String sessionId = redis.opsForValue().get(refreshKey(refreshTokenHash));
        if (sessionId == null) {
            return Optional.empty();
        }
        return findBySessionId(UUID.fromString(sessionId))
            .filter(record -> record.refreshTokenHash().equals(refreshTokenHash));
    }

    @Override
    public boolean rotate(
        UUID sessionId,
        String expectedRefreshTokenHash,
        String newRefreshTokenHash,
        Instant newExpiresAt,
        Instant rotatedAt
    ) {
        Duration ttl = positiveTtl(newExpiresAt);
        Long result = redis.execute(
            ROTATE_SCRIPT,
            List.of(
                sessionKey(sessionId),
                refreshKey(expectedRefreshTokenHash),
                refreshKey(newRefreshTokenHash)
            ),
            expectedRefreshTokenHash,
            newRefreshTokenHash,
            newExpiresAt.toString(),
            rotatedAt.toString(),
            Long.toString(ttl.toSeconds()),
            sessionId.toString()
        );
        return Long.valueOf(1).equals(result);
    }

    @Override
    public boolean revoke(UUID sessionId, Instant revokedAt) {
        Long result = redis.execute(
            REVOKE_SCRIPT,
            List.of(sessionKey(sessionId)),
            REFRESH_PREFIX,
            revokedAt.toString()
        );
        return Long.valueOf(1).equals(result);
    }

    private TeacherSessionRecord toRecord(Map<Object, Object> values) {
        String revokedAt = optionalValue(values, "revoked_at");
        return new TeacherSessionRecord(
            UUID.fromString(requiredValue(values, "session_id")),
            UUID.fromString(requiredValue(values, "user_id")),
            requiredValue(values, "external_subject"),
            requiredValue(values, "refresh_hash"),
            TeacherSessionStatus.valueOf(requiredValue(values, "status")),
            Instant.parse(requiredValue(values, "issued_at")),
            Instant.parse(requiredValue(values, "expires_at")),
            revokedAt == null ? null : Instant.parse(revokedAt)
        );
    }

    private Duration positiveTtl(Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("teacher session expiry must be in the future");
        }
        return ttl;
    }

    private static String requiredValue(Map<Object, Object> values, String key) {
        String value = optionalValue(values, key);
        if (value == null) {
            throw new IllegalStateException("teacher session record is incomplete");
        }
        return value;
    }

    private static String optionalValue(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static String sessionKey(UUID sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private static String refreshKey(String refreshTokenHash) {
        return REFRESH_PREFIX + refreshTokenHash;
    }
}
