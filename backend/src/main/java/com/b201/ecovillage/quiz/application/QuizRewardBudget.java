package com.b201.ecovillage.quiz.application;

public record QuizRewardBudget(
    long nonRewardIssuance,
    long existingRewardIssuance,
    long candidateRewardIssuance,
    int rewardShareBasisPoints,
    boolean warning,
    boolean blocked
) {
}
