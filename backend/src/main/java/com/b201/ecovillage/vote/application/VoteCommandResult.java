package com.b201.ecovillage.vote.application;

import java.util.UUID;

public record VoteCommandResult(
    UUID voteId,
    String status,
    long stateVersion,
    UUID selectedOptionId,
    UUID transactionGroupId
) {
}
