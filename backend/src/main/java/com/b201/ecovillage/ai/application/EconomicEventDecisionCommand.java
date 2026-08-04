package com.b201.ecovillage.ai.application;

import java.util.Map;
import java.util.UUID;

/**
 * API-AI-006 경제 이벤트 승인·거절 명령이다.
 *
 * @param edits 승인 시 확정할 경제 정책 값이다. 템플릿이 허용한 항목만 담을 수 있다.
 */
public record EconomicEventDecisionCommand(
    String teacherSubject,
    UUID classroomId,
    UUID eventId,
    UUID requestId,
    UUID correlationId,
    String decision,
    Map<String, Object> edits,
    String reason,
    Long expectedEventVersion,
    Long expectedStateVersion
) {
}
