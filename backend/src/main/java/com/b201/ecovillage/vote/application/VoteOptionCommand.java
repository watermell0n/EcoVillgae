package com.b201.ecovillage.vote.application;

public record VoteOptionCommand(
    String label,
    String description,
    Long budgetAmount
) {
}
