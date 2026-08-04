package com.b201.ecovillage.ai.application;

import java.util.UUID;

public record AiSettingsResult(
    UUID aiResourceId,
    String status,
    long stateVersion
) {
}
