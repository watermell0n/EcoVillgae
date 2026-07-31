package com.b201.ecovillage.quiz.application;

public record QuizOptionCommand(
    String optionKey,
    String optionText,
    boolean correct,
    int sortOrder
) {
}
