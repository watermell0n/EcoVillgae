package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;

public record QuizQuestionCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    QuizQuestionCommandType command,
    UUID questionId,
    QuizContentType contentType,
    QuizQuestionType questionType,
    String prompt,
    List<QuizOptionCommand> options,
    String explanation,
    Integer expectedQuestionVersion
) {
}
