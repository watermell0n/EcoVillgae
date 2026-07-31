package com.b201.ecovillage.quiz.application;

import java.time.Instant;
import java.util.UUID;

public record GameAnswerCommand(
    String studentSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    UUID sessionId,
    UUID questionId,
    UUID optionId,
    Instant clientAnsweredAt
) {
}
