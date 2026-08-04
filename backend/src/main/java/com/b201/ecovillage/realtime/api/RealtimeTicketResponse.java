package com.b201.ecovillage.realtime.api;

import java.time.Instant;

public record RealtimeTicketResponse(
    String ticket,
    Instant expiresAt
) {
}
