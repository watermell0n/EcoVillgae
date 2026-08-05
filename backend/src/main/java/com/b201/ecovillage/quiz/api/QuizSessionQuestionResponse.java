package com.b201.ecovillage.quiz.api;

import java.util.List;
import java.util.UUID;

public record QuizSessionQuestionResponse(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    String prompt,
    List<Option> options
) {
    /** 학생 앱이 답 제출에 쓰는 option_id를 함께 내려줍니다. */
    public record Option(
        UUID optionId,
        String optionKey,
        String optionText
    ) {
    }
}
