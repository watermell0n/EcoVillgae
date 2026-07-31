package com.b201.ecovillage.vote.application;

import java.util.List;
import java.util.UUID;

public record VoteCommand(
    String teacherSubject,
    UUID classroomId,
    UUID voteId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    VoteCommandType command,
    String title,
    String description,
    List<VoteOptionCommand> options,
    List<UUID> participantIds,
    Boolean publicDisplayEnabled,
    String selectedUse
) {
}
