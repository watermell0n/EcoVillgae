package com.b201.ecovillage.realtime.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PublicDisplayView(
    UUID activityId,
    String scene,
    Instant serverTime,
    Instant endsAt,
    Map<String, Object> publicData
) {
    public PublicDisplayView {
        publicData = Map.copyOf(publicData);
    }
}
