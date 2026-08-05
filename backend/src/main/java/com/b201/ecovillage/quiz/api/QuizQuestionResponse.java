package com.b201.ecovillage.quiz.api;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;

public record QuizQuestionResponse(
    UUID resourceId,
    UUID versionId,
    String label,
    String status,
    int version,
    // 퀴즈 세션 시작(START)은 문항이 아니라 문항 버전 id로 문항을 지정한다.
    UUID questionVersionId,
    long stateVersion,
    QuizContentType contentType,
    QuizQuestionType questionType,
    String prompt,
    String explanation,
    String source,
    UUID aiRunId,
    List<OptionResponse> options
) {
    public QuizQuestionResponse {
        options = List.copyOf(options);
    }

    public record OptionResponse(
        UUID optionId,
        String optionKey,
        String optionText,
        boolean correct,
        int sortOrder
    ) {
    }
}
