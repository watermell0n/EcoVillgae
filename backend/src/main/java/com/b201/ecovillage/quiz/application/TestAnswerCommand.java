package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record TestAnswerCommand(UUID questionId, UUID optionId) {
}
