package com.b201.ecovillage.vote.application;

import java.util.UUID;

public record VoteBallotCommand(
    String studentSubject,
    UUID classroomId,
    UUID voteId,
    UUID optionId,
    UUID requestId,
    UUID correlationId
) {
}
