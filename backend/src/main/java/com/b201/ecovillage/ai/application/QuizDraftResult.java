package com.b201.ecovillage.ai.application;

import java.util.List;
import java.util.UUID;

public record QuizDraftResult(
    UUID operationId,
    String status,
    String statusUrl,
    UUID aiRunId,
    List<UUID> questionIds,
    boolean fallbackUsed,
    String fallbackCode
) {
    public QuizDraftResult {
        questionIds = List.copyOf(questionIds);
    }
}
