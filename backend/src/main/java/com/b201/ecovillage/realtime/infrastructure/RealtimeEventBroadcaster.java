package com.b201.ecovillage.realtime.infrastructure;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import com.b201.ecovillage.realtime.application.RealtimeEventEnvelope;

import tools.jackson.databind.ObjectMapper;

@Component
public class RealtimeEventBroadcaster {

    private final ConcurrentMap<String, RealtimeConnection> connections =
        new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RealtimeEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void register(RealtimeConnection connection) {
        connections.put(connection.sessionId(), connection);
    }

    RealtimeConnection find(String sessionId) {
        return connections.get(sessionId);
    }

    void remove(String sessionId) {
        connections.remove(sessionId);
    }

    public void broadcast(RealtimeEventEnvelope event) {
        connections.values().forEach(connection -> {
            synchronized (connection) {
                connection.subscriptions().values().forEach(subscription -> {
                    if (
                        subscription.matches(connection.ticket(), event)
                            && event.sequence() > subscription.lastDeliveredSequence()
                    ) {
                        sendEvent(connection, subscription, event);
                    }
                });
            }
        });
    }

    void sendEvent(
        RealtimeConnection connection,
        RealtimeSubscription subscription,
        RealtimeEventEnvelope event
    ) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "domain.event");
        message.put("event_id", event.eventId());
        message.put("event_type", event.eventType());
        message.put("schema_version", 1);
        message.put("channel_id", subscription.channel().channelId());
        message.put("subscription_id", subscription.id());
        message.put("sequence", event.sequence());
        message.put("occurred_at", event.occurredAt());
        message.put("classroom_id", event.classroomId());
        message.put("resource_id", event.resourceId());
        message.put("state_version", event.stateVersion());
        message.put("audience", audience(subscription));
        message.put("payload", event.payload());
        try {
            connection.send(objectMapper, message);
            subscription.delivered(event.sequence());
        } catch (IOException exception) {
            close(connection);
        }
    }

    void closeExpired(java.time.Instant now) {
        connections.values().stream()
            .filter(connection -> !connection.expiresAt().isAfter(now))
            .forEach(this::close);
    }

    private void close(RealtimeConnection connection) {
        remove(connection.sessionId());
        try {
            connection.session().close(CloseStatus.NORMAL.withReason("connection expired"));
        } catch (IOException ignored) {
            // The connection is already unavailable.
        }
    }

    private static String audience(RealtimeSubscription subscription) {
        return switch (subscription.channel()) {
            case STUDENT_PRIVATE -> "SELF";
            case TEACHER_CONTROL -> "TEACHER";
            case CLASSROOM, ECONOMY, INVESTMENT -> "CLASSROOM";
            case DISPLAY -> "PUBLIC";
            default -> "PARTICIPANT";
        };
    }
}
