package com.b201.ecovillage.trade.application;

import java.util.List;
import java.util.UUID;

public record CreateTradeCommand(
    String studentSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    UUID counterpartStudentId,
    long offeredAmount,
    long requestedAmount,
    List<UUID> offeredItemIds,
    List<UUID> requestedItemIds,
    String description
) {
}
