package com.b201.ecovillage.vote.application;

import java.util.UUID;

public record VoteOptionView(
    UUID optionId,
    String label,
    String description,
    long budgetAmount,
    Long voteCount
) {
}
