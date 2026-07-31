package com.b201.ecovillage.vote.application;

import java.time.Instant;

public record VoteBallotResult(
    String receiptHash,
    Instant castAt
) {
}
