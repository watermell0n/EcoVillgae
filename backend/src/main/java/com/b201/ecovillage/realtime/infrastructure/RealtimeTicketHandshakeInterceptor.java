package com.b201.ecovillage.realtime.infrastructure;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.b201.ecovillage.realtime.application.RealtimeTicket;
import com.b201.ecovillage.realtime.application.RealtimeTicketStore;

@Component
public class RealtimeTicketHandshakeInterceptor implements HandshakeInterceptor {

    static final String TICKET_ATTRIBUTE = RealtimeTicket.class.getName();

    private final RealtimeTicketStore ticketStore;

    public RealtimeTicketHandshakeInterceptor(RealtimeTicketStore ticketStore) {
        this.ticketStore = ticketStore;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String rawTicket = UriComponentsBuilder
            .fromUri(request.getURI())
            .build()
            .getQueryParams()
            .getFirst("ticket");
        try {
            RealtimeTicket ticket = ticketStore.consume(rawTicket).orElse(null);
            if (ticket == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(TICKET_ATTRIBUTE, ticket);
            return true;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        // No raw ticket data is retained after the one-time consume operation.
    }
}
