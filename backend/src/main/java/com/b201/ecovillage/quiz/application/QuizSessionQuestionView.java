package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

public record QuizSessionQuestionView(
    UUID resourceId,
    UUID quizQuestionVersionId,
    String label,
    String status,
    int version,
    long stateVersion,
    String prompt,
    List<Option> options
) {
    /**
     * 학생 앱은 답을 제출할 때 option_id를 그대로 되돌려 보내야 합니다. 보기 글만 내려주면
     * 제출할 값이 없어 학생 화면에서 선택지를 누를 수 없습니다.
     */
    public record Option(
        UUID optionId,
        String optionKey,
        String optionText
    ) {
    }
}
