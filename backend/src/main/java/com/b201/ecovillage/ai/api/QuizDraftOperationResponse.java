package com.b201.ecovillage.ai.api;

import java.util.List;
import java.util.UUID;

/**
 * API-QZ-002 접수 결과와 생성된 검토 대상 문항 식별자를 함께 반환한다.
 */
public record QuizDraftOperationResponse(
    UUID operationId,
    String status,
    String statusUrl,
    UUID aiRunId,
    String reviewStatus,
    List<UUID> questionIds
) {
    public QuizDraftOperationResponse {
        questionIds = List.copyOf(questionIds);
    }
}
