package com.b201.ecovillage.realtime.infrastructure;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.b201.ecovillage.realtime.application.RealtimeTicket;

import tools.jackson.databind.ObjectMapper;

final class RealtimeConnection {

    private final UUID id;
    private final WebSocketSession session;
    private final RealtimeTicket ticket;
    private final Instant expiresAt;
    private final Map<UUID, RealtimeSubscription> subscriptions = new LinkedHashMap<>();

    RealtimeConnection(
        UUID id,
        WebSocketSession session,
        RealtimeTicket ticket,
        Instant expiresAt
    ) {
        this.id = id;
        this.session = session;
        this.ticket = ticket;
        this.expiresAt = expiresAt;
    }

    UUID id() {
        return id;
    }

    String sessionId() {
        return session.getId();
    }

    WebSocketSession session() {
        return session;
    }

    RealtimeTicket ticket() {
        return ticket;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Map<UUID, RealtimeSubscription> subscriptions() {
        return subscriptions;
    }

    void send(ObjectMapper objectMapper, Object payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }
}
