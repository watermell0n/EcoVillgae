package com.b201.ecovillage.realtime.application;

import java.time.Instant;
import java.util.UUID;

public record DisplaySessionResult(
    UUID displaySessionId,
    String displayToken,
    Instant expiresAt,
    String status
) {
}
