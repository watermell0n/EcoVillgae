package com.b201.ecovillage.ai.api;

import java.util.UUID;

public record AiSettingsResponse(
    UUID aiResourceId,
    String status,
    long stateVersion
) {
}
