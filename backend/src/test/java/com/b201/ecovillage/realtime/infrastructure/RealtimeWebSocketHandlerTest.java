package com.b201.ecovillage.realtime.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.realtime.application.RealtimeEventEnvelope;
import com.b201.ecovillage.realtime.application.RealtimeResourceAuthorizationService;
import com.b201.ecovillage.realtime.application.RealtimeSubscriptionQueryService;
import com.b201.ecovillage.realtime.application.RealtimeSubscriptionSnapshot;
import com.b201.ecovillage.realtime.application.RealtimeTicket;
import com.b201.ecovillage.realtime.application.StudentPresenceService;
import com.b201.ecovillage.realtime.config.RealtimeProperties;

import tools.jackson.databind.ObjectMapper;

class RealtimeWebSocketHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void subscribesDeliversAndAcknowledgesDomainEvent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeEventBroadcaster broadcaster = new RealtimeEventBroadcaster(objectMapper);
        RealtimeSubscriptionQueryService queryService = mock(
            RealtimeSubscriptionQueryService.class
        );
        RealtimeResourceAuthorizationService resourceAuthorization = mock(
            RealtimeResourceAuthorizationService.class
        );
        UUID classroomId = UUID.randomUUID();
        RealtimeTicket ticket = ticket(classroomId);
        when(queryService.load(any(), any(), any(), any(Long.class)))
            .thenReturn(new RealtimeSubscriptionSnapshot(0, 1, List.of(), false, "/snapshot"));

        List<TextMessage> sent = new ArrayList<>();
        WebSocketSession session = session(ticket, sent);
        RealtimeWebSocketHandler handler = handler(
            broadcaster,
            queryService,
            resourceAuthorization,
            objectMapper
        );
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "subscription.subscribe",
            "request_id", UUID.randomUUID(),
            "channel_id", "WS-TCH-001",
            "resource_id", classroomId,
            "last_sequence", 0
        ))));

        broadcaster.broadcast(new RealtimeEventEnvelope(
            UUID.randomUUID(),
            classroomId,
            UUID.randomUUID(),
            "TEACHER_CONTROL",
            null,
            1,
            "notification.created",
            "AI_PROPOSAL",
            UUID.randomUUID(),
            1,
            NOW,
            Map.of("title", "AI proposal completed")
        ));

        assertEquals("connection.ready", type(objectMapper, sent.get(0)));
        assertEquals("subscription.accepted", type(objectMapper, sent.get(1)));
        assertEquals("domain.event", type(objectMapper, sent.get(2)));
        String subscriptionId = objectMapper.readTree(sent.get(2).getPayload())
            .get("subscription_id")
            .asText();
        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "event.ack",
            "subscription_id", subscriptionId,
            "sequence", 1
        ))));

        verify(resourceAuthorization).requireAuthorized(
            ticket,
            com.b201.ecovillage.realtime.application.RealtimeChannel.TEACHER_CONTROL,
            classroomId
        );
        assertTrue(sent.stream().noneMatch(message -> {
            try {
                return "protocol.error".equals(type(objectMapper, message));
            } catch (Exception exception) {
                return false;
            }
        }));
    }

    private static RealtimeWebSocketHandler handler(
        RealtimeEventBroadcaster broadcaster,
        RealtimeSubscriptionQueryService queryService,
        RealtimeResourceAuthorizationService resourceAuthorization,
        ObjectMapper objectMapper
    ) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new RealtimeWebSocketHandler(
            broadcaster,
            queryService,
            resourceAuthorization,
            new StudentPresenceService(),
            new RealtimeProperties(
                false,
                Duration.ofSeconds(60),
                Duration.ofHours(8),
                Duration.ofSeconds(25),
                100,
                250
            ),
            new UuidV7Generator(clock, new java.security.SecureRandom()),
            objectMapper,
            clock
        );
    }

    private static WebSocketSession session(
        RealtimeTicket ticket,
        List<TextMessage> sent
    ) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RealtimeTicketHandshakeInterceptor.TICKET_ATTRIBUTE, ticket);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("socket-1");
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        return session;
    }

    private static RealtimeTicket ticket(UUID classroomId) {
        return new RealtimeTicket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "teacher-subject",
            UUID.randomUUID(),
            "TCH",
            classroomId,
            Set.of("WS-TCH-001"),
            Set.of(classroomId),
            NOW.plusSeconds(60)
        );
    }

    private static String type(ObjectMapper objectMapper, TextMessage message) throws Exception {
        return objectMapper.readTree(message.getPayload()).get("type").asText();
    }
}
