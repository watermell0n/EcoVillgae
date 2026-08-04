package com.b201.ecovillage.realtime.infrastructure;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.realtime.application.RealtimeChannel;
import com.b201.ecovillage.realtime.application.RealtimeProtocolException;
import com.b201.ecovillage.realtime.application.RealtimeResourceAuthorizationService;
import com.b201.ecovillage.realtime.application.RealtimeSubscriptionQueryService;
import com.b201.ecovillage.realtime.application.RealtimeSubscriptionSnapshot;
import com.b201.ecovillage.realtime.application.RealtimeTicket;
import com.b201.ecovillage.realtime.config.RealtimeProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeEventBroadcaster broadcaster;
    private final RealtimeSubscriptionQueryService queryService;
    private final RealtimeResourceAuthorizationService resourceAuthorizationService;
    private final RealtimeProperties properties;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RealtimeWebSocketHandler(
        RealtimeEventBroadcaster broadcaster,
        RealtimeSubscriptionQueryService queryService,
        RealtimeResourceAuthorizationService resourceAuthorizationService,
        RealtimeProperties properties,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.broadcaster = broadcaster;
        this.queryService = queryService;
        this.resourceAuthorizationService = resourceAuthorizationService;
        this.properties = properties;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        RealtimeTicket ticket = (RealtimeTicket) session
            .getAttributes()
            .get(RealtimeTicketHandshakeInterceptor.TICKET_ATTRIBUTE);
        if (ticket == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("ticket required"));
            return;
        }
        UUID connectionId = uuidGenerator.generate();
        Instant expiresAt = clock.instant().plus(properties.connectionTtl());
        RealtimeConnection connection = new RealtimeConnection(
            connectionId,
            session,
            ticket,
            expiresAt
        );
        broadcaster.register(connection);
        synchronized (connection) {
            connection.send(objectMapper, Map.of(
                "type", "connection.ready",
                "connection_id", connectionId,
                "server_time", clock.instant(),
                "heartbeat_interval_seconds", properties.heartbeatInterval().toSeconds(),
                "connection_expires_at", expiresAt
            ));
        }
    }

    @Override
    protected void handleTextMessage(
        WebSocketSession session,
        TextMessage message
    ) throws Exception {
        RealtimeConnection connection = broadcaster.find(session.getId());
        if (connection == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (message.getPayloadLength() > 65_536) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        if (!connection.expiresAt().isAfter(clock.instant())) {
            session.close(CloseStatus.NORMAL.withReason("connection expired"));
            return;
        }

        try {
            JsonNode request = objectMapper.readTree(message.getPayload());
            String type = requiredText(request, "type");
            switch (type) {
                case "subscription.subscribe" -> subscribe(connection, request);
                case "subscription.unsubscribe" -> unsubscribe(connection, request);
                case "connection.ping" -> pong(connection, request);
                case "event.ack" -> acknowledge(connection, request);
                default -> throw new RealtimeProtocolException(
                    "UNSUPPORTED_MESSAGE_TYPE",
                    "Unsupported WebSocket message type"
                );
            }
        } catch (RealtimeProtocolException exception) {
            sendError(connection, exception.getCode(), exception.getMessage());
        } catch (Exception exception) {
            sendError(connection, "INVALID_MESSAGE", "Malformed WebSocket message");
        }
    }

    @Override
    public void afterConnectionClosed(
        WebSocketSession session,
        CloseStatus status
    ) {
        broadcaster.remove(session.getId());
    }

    @Override
    public void handleTransportError(
        WebSocketSession session,
        Throwable exception
    ) throws Exception {
        broadcaster.remove(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void subscribe(RealtimeConnection connection, JsonNode request) throws IOException {
        UUID requestId = requiredUuid(request, "request_id");
        RealtimeChannel channel = RealtimeChannel.require(requiredText(request, "channel_id"));
        RealtimeTicket ticket = connection.ticket();
        if (!ticket.channelIds().contains(channel.channelId())) {
            throw new RealtimeProtocolException(
                "CHANNEL_NOT_IN_TICKET",
                "The ticket does not allow this channel"
            );
        }
        channel.requireRole(ticket.role());
        UUID resourceId = authorizedResource(ticket, channel, optionalUuid(request, "resource_id"));
        resourceAuthorizationService.requireAuthorized(ticket, channel, resourceId);
        long lastSequence = optionalLong(request, "last_sequence", 0);

        synchronized (connection) {
            RealtimeSubscriptionSnapshot snapshot = queryService.load(
                ticket,
                channel,
                resourceId,
                lastSequence
            );
            RealtimeSubscription subscription = new RealtimeSubscription(
                uuidGenerator.generate(),
                channel,
                resourceId,
                lastSequence
            );
            connection.subscriptions().put(subscription.id(), subscription);
            Map<String, Object> accepted = new LinkedHashMap<>();
            accepted.put("type", "subscription.accepted");
            accepted.put("request_id", requestId);
            accepted.put("subscription_id", subscription.id());
            accepted.put("channel_id", channel.channelId());
            accepted.put("resource_id", resourceId);
            accepted.put("current_sequence", snapshot.currentSequence());
            accepted.put("snapshot_url", snapshot.snapshotUrl());
            connection.send(objectMapper, accepted);

            if (snapshot.recoveryRequired()) {
                connection.send(objectMapper, Map.of(
                    "type", "recovery.required",
                    "subscription_id", subscription.id(),
                    "channel_id", channel.channelId(),
                    "current_sequence", snapshot.currentSequence(),
                    "retention_from_sequence", snapshot.retentionFromSequence(),
                    "snapshot_url", snapshot.snapshotUrl()
                ));
                subscription.delivered(snapshot.currentSequence());
                return;
            }
            snapshot.events().forEach(event -> broadcaster.sendEvent(connection, subscription, event));
        }
    }

    private void unsubscribe(RealtimeConnection connection, JsonNode request) throws IOException {
        UUID requestId = requiredUuid(request, "request_id");
        UUID subscriptionId = requiredUuid(request, "subscription_id");
        synchronized (connection) {
            connection.subscriptions().remove(subscriptionId);
            connection.send(objectMapper, Map.of(
                "type", "subscription.removed",
                "request_id", requestId,
                "subscription_id", subscriptionId
            ));
        }
    }

    private void acknowledge(RealtimeConnection connection, JsonNode request) {
        UUID subscriptionId = requiredUuid(request, "subscription_id");
        long sequence = optionalLong(request, "sequence", -1);
        synchronized (connection) {
            RealtimeSubscription subscription = connection.subscriptions().get(subscriptionId);
            if (subscription == null || sequence < 0) {
                throw new RealtimeProtocolException(
                    "UNKNOWN_SUBSCRIPTION",
                    "The subscription does not exist"
                );
            }
            try {
                subscription.acknowledge(sequence);
            } catch (IllegalArgumentException exception) {
                throw new RealtimeProtocolException("INVALID_ACK", exception.getMessage());
            }
        }
    }

    private void pong(RealtimeConnection connection, JsonNode request) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "connection.pong");
        response.put("server_time", clock.instant());
        response.put(
            "client_time",
            request.hasNonNull("client_time") ? request.get("client_time").asText() : null
        );
        synchronized (connection) {
            connection.send(objectMapper, response);
        }
    }

    private UUID authorizedResource(
        RealtimeTicket ticket,
        RealtimeChannel channel,
        UUID requestedResourceId
    ) {
        UUID resourceId = switch (channel.scope()) {
            case CLASSROOM -> requestedResourceId == null
                ? ticket.classroomId()
                : requestedResourceId;
            case SELF -> requestedResourceId == null ? ticket.actorId() : requestedResourceId;
            case RESOURCE -> requestedResourceId;
        };
        if (resourceId == null || !ticket.resourceIds().contains(resourceId)) {
            throw new RealtimeProtocolException(
                "RESOURCE_NOT_IN_TICKET",
                "The ticket does not allow this resource"
            );
        }
        if (
            channel.scope() == RealtimeChannel.Scope.CLASSROOM
                && !ticket.classroomId().equals(resourceId)
        ) {
            throw new RealtimeProtocolException(
                "CLASSROOM_SCOPE_VIOLATION",
                "The subscription classroom does not match the ticket"
            );
        }
        if (
            channel.scope() == RealtimeChannel.Scope.SELF
                && !ticket.actorId().equals(resourceId)
        ) {
            throw new RealtimeProtocolException(
                "RESOURCE_NOT_OWNED",
                "A private subscription must target the authenticated actor"
            );
        }
        return resourceId;
    }

    private void sendError(
        RealtimeConnection connection,
        String code,
        String message
    ) throws IOException {
        synchronized (connection) {
            connection.send(objectMapper, Map.of(
                "type", "protocol.error",
                "code", code,
                "message", message,
                "retryable", false
            ));
        }
    }

    private static String requiredText(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new RealtimeProtocolException("INVALID_MESSAGE", field + " is required");
        }
        return value.asText();
    }

    private static UUID requiredUuid(JsonNode request, String field) {
        UUID value = optionalUuid(request, field);
        if (value == null) {
            throw new RealtimeProtocolException("INVALID_MESSAGE", field + " is required");
        }
        return value;
    }

    private static UUID optionalUuid(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException exception) {
            throw new RealtimeProtocolException("INVALID_MESSAGE", field + " must be a UUID");
        }
    }

    private static long optionalLong(JsonNode request, String field, long fallback) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber()) {
            throw new RealtimeProtocolException("INVALID_MESSAGE", field + " must be an integer");
        }
        return value.asLong();
    }
}
