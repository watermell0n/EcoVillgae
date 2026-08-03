package com.b201.ecovillage.ai.api;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * API-AI-006 요청이다. 계약의 핵심 입력은
 * {@code decision=APPROVE|REJECT, edits?, reason, expected_event_version}이며
 * 나머지 네 필드는 학급 변경 API 공통 봉투다.
 *
 * @param expectedStateVersion 학급 상태 버전이다. 보내면 경제 정책 반영 전에 함께 검사한다.
 * @param edits 승인 시 교사가 확정하는 경제 정책 값이다. 템플릿이 허용한 항목만 담을 수 있다.
 */
public record EconomicEventDecisionRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z-]+$|^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z-]+$|^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String decision,
    Map<String, Object> edits,
    @NotBlank String reason,
    @NotNull @Min(0) Long expectedEventVersion
) {
}
