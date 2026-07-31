package com.b201.ecovillage.realtime.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.b201.ecovillage.realtime.application.DisplaySessionRecord;
import com.b201.ecovillage.realtime.application.DisplaySessionStore;

@Repository
public class RedisDisplaySessionStore implements DisplaySessionStore {

    private static final String TOKEN_PREFIX = "auth:display-session:v1:";
    private static final String ID_PREFIX = "auth:display-session-id:v1:";
    private static final String ACTIVITY_PREFIX = "auth:display-session-activity:v1:";

    private final StringRedisTemplate redis;

    public RedisDisplaySessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String tokenHash, DisplaySessionRecord record) {
        Duration ttl = Duration.between(Instant.now(), record.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("display session expiry must be in the future");
        }
        String tokenKey = tokenKey(tokenHash);
        Map<String, String> values = new HashMap<>();
        values.put("display_session_id", record.displaySessionId().toString());
        values.put("classroom_id", record.classroomId().toString());
        values.put("activity_id", record.activityId().toString());
        values.put("public_fields", String.join(",", record.publicFields()));
        values.put("issued_by_user_id", record.issuedByUserId().toString());
        values.put("issued_at", record.issuedAt().toString());
        values.put("expires_at", record.expiresAt().toString());
        redis.opsForHash().putAll(tokenKey, values);
        redis.expire(tokenKey, ttl);
        redis.opsForValue().set(
            idKey(record.displaySessionId()),
            tokenHash,
            ttl
        );
        String activityKey = activityKey(record.activityId());
        redis.opsForSet().add(activityKey, record.displaySessionId().toString());
        redis.expire(activityKey, ttl);
    }

    @Override
    public Optional<DisplaySessionRecord> find(
        UUID displaySessionId,
        String tokenHash
    ) {
        String expectedHash = redis.opsForValue().get(idKey(displaySessionId));
        if (expectedHash == null || !expectedHash.equals(tokenHash)) {
            return Optional.empty();
        }
        Map<Object, Object> values = redis.opsForHash().entries(tokenKey(tokenHash));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        DisplaySessionRecord record = toRecord(values);
        return record.displaySessionId().equals(displaySessionId)
            ? Optional.of(record)
            : Optional.empty();
    }

    @Override
    public boolean revoke(UUID displaySessionId) {
        String idKey = idKey(displaySessionId);
        String tokenHash = redis.opsForValue().get(idKey);
        if (tokenHash == null) {
            return false;
        }
        Map<Object, Object> values = redis.opsForHash().entries(tokenKey(tokenHash));
        if (!values.isEmpty()) {
            Object activityId = values.get("activity_id");
            if (activityId != null) {
                redis.opsForSet().remove(
                    activityKey(UUID.fromString(activityId.toString())),
                    displaySessionId.toString()
                );
            }
        }
        redis.delete(Set.of(idKey, tokenKey(tokenHash)));
        return true;
    }

    @Override
    public void revokeAllForActivity(UUID activityId) {
        String activityKey = activityKey(activityId);
        Set<String> sessionIds = redis.opsForSet().members(activityKey);
        if (sessionIds != null) {
            sessionIds.forEach(id -> revoke(UUID.fromString(id)));
        }
        redis.delete(activityKey);
    }

    private static DisplaySessionRecord toRecord(Map<Object, Object> values) {
        Set<String> publicFields = Arrays.stream(
            value(values, "public_fields").split(",")
        ).filter(field -> !field.isBlank()).collect(Collectors.toSet());
        return new DisplaySessionRecord(
            UUID.fromString(value(values, "display_session_id")),
            UUID.fromString(value(values, "classroom_id")),
            UUID.fromString(value(values, "activity_id")),
            publicFields,
            UUID.fromString(value(values, "issued_by_user_id")),
            Instant.parse(value(values, "issued_at")),
            Instant.parse(value(values, "expires_at"))
        );
    }

    private static String value(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("display session record is incomplete");
        }
        return value.toString();
    }

    private static String tokenKey(String tokenHash) {
        return TOKEN_PREFIX + tokenHash;
    }

    private static String idKey(UUID displaySessionId) {
        return ID_PREFIX + displaySessionId;
    }

    private static String activityKey(UUID activityId) {
        return ACTIVITY_PREFIX + activityId;
    }
}
