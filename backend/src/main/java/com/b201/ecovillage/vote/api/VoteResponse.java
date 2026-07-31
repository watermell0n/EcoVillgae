package com.b201.ecovillage.vote.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VoteResponse(
    UUID voteId,
    String title,
    String description,
    boolean anonymous,
    String status,
    long stateVersion,
    Instant opensAt,
    Instant closesAt,
    long publicFundBalance,
    long participantCount,
    long ballotCount,
    Boolean eligible,
    Boolean hasVoted,
    UUID selectedOptionId,
    String selectedUse,
    UUID transactionGroupId,
    List<VoteOptionResponse> options
) {
}
