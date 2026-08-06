package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;

public record QuizQuestionView(
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
    /** 이 학급에서 퀴즈로 한 번이라도 열린 문항인지입니다. 열린 문항은 다시 쓸 수 없습니다. */
    boolean alreadyUsedInSession,
    List<Option> options
) {
    public QuizQuestionView {
        options = List.copyOf(options);
    }

    public record Option(
        UUID optionId,
        String optionKey,
        String optionText,
        boolean correct,
        int sortOrder
    ) {
    }
}
