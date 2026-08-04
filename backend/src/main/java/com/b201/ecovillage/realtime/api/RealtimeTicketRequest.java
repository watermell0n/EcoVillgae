package com.b201.ecovillage.realtime.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RealtimeTicketRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotEmpty @Size(max = 10)
    Set<@Pattern(regexp = "^WS-[A-Z]+-\\d{3}$") String> channelIds,
    @Size(max = 64) Set<UUID> resourceIds
) {
}
