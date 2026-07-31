package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

public record TestAnswerSheetCommand(
    String studentSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    UUID sessionId,
    List<TestAnswerCommand> answers,
    int answerSheetVersion,
    boolean submit
) {
}
