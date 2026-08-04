package com.b201.ecovillage.realtime.infrastructure;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.realtime.application.IssuedRealtimeTicket;
import com.b201.ecovillage.realtime.application.RealtimeTicket;
import com.b201.ecovillage.realtime.application.RealtimeTicketStore;

@Component
@ConditionalOnProperty(
    prefix = "ecovillage.realtime",
    name = "redis-enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class InMemoryRealtimeTicketStore implements RealtimeTicketStore {

    private final ConcurrentMap<String, RealtimeTicket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom;
    private final Clock clock;

    public InMemoryRealtimeTicketStore(SecureRandom secureRandom, Clock clock) {
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Override
    public IssuedRealtimeTicket issue(RealtimeTicket ticket) {
        String secret = RealtimeTicketSecrets.newSecret(secureRandom);
        tickets.put(RealtimeTicketSecrets.hash(secret), ticket);
        return new IssuedRealtimeTicket(secret, ticket.expiresAt());
    }

    @Override
    public Optional<RealtimeTicket> consume(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            return Optional.empty();
        }
        RealtimeTicket ticket = tickets.remove(RealtimeTicketSecrets.hash(rawTicket));
        if (ticket == null || ticket.isExpiredAt(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(ticket);
    }
}
