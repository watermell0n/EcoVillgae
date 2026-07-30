package com.b201.ecovillage.vote.api;

import java.util.UUID;

public record VoteOptionResponse(
    UUID optionId,
    String label,
    String description,
    long budgetAmount,
    Long voteCount
) {
}
