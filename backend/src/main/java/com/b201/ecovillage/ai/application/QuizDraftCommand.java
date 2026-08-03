package com.b201.ecovillage.ai.application;

import java.util.UUID;

import com.b201.ecovillage.ai.contract.QuizDraftContract;

public record QuizDraftCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    int grade,
    String topic,
    String learningObjective,
    QuizDraftContract.Category category,
    QuizDraftContract.QuestionType questionType,
    int questionCount
) {
}
