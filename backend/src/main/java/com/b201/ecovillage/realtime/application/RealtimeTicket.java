package com.b201.ecovillage.realtime.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RealtimeTicket(
    UUID ticketId,
    UUID sessionId,
    String subject,
    UUID actorId,
    String role,
    UUID classroomId,
    Set<String> channelIds,
    Set<UUID> resourceIds,
    Instant expiresAt
) {

    public RealtimeTicket {
        channelIds = Set.copyOf(channelIds);
        resourceIds = Set.copyOf(resourceIds);
    }

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }
}
