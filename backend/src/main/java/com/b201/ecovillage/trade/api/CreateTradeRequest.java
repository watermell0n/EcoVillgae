package com.b201.ecovillage.trade.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateTradeRequest(
    Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull UUID counterpartStudentId,
    @PositiveOrZero long offeredAmount,
    @PositiveOrZero long requestedAmount,
    @NotNull List<@NotNull UUID> offeredItemIds,
    @NotNull List<@NotNull UUID> requestedItemIds,
    @Size(max = 2000) String description
) {
}
