package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

public record CreditEventResponse(
    UUID eventId,
    String eventType,
    int scoreDelta,
    int scoreBefore,
    int scoreAfter,
    String levelBefore,
    String levelAfter,
    String reason,
    Instant occurredAt
) { }
