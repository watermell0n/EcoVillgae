package com.b201.ecovillage.ai.application;

import java.util.UUID;

public record EconomicEventDraftResult(
    UUID operationId,
    String status,
    String statusUrl,
    UUID draftId,
    UUID aiRunId,
    boolean fallbackUsed,
    String fallbackCode
) {
}
