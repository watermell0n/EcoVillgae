package com.b201.ecovillage.quiz.api;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.quiz.application.PresentationMoveCommandType;

/**
 * 수업 자료를 넘기는 요청이다.
 * {@code page}는 {@code GOTO}에서만 쓰고, 다음·이전은 서버가 현재 커서에서 계산한다.
 */
public record PresentationMoveRequest(
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,

    @NotNull PresentationMoveCommandType command,

    @Min(1) Integer page
) {
}
