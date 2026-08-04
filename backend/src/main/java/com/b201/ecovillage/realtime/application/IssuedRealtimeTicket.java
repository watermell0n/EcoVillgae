package com.b201.ecovillage.realtime.application;

import java.time.Instant;

public record IssuedRealtimeTicket(
    String ticket,
    Instant expiresAt
) {
}
