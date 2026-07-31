package com.b201.ecovillage.vote.api;

import java.time.Instant;

public record VoteBallotResponse(
    String receiptHash,
    Instant castAt
) {
}
