package com.b201.ecovillage.vote.api;

import java.util.UUID;

public record VoteCommandResponse(
    UUID voteId,
    String status,
    long stateVersion,
    UUID selectedOptionId,
    UUID transactionGroupId
) {
}
