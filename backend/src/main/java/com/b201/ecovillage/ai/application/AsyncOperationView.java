package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.UUID;

public record AsyncOperationView(
    UUID operationId,
    String operationType,
    String status,
    int progressPercent,
    String resultUrl,
    String errorCode,
    Instant startedAt,
    Instant completedAt,
    Instant expiresAt,
    long stateVersion
) {
}
