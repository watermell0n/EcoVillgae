package com.b201.ecovillage.character.api;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CharacterEquipmentRequest(
    @Min(0) Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String command
) {
}
