package com.b201.ecovillage.character.api;

import java.util.UUID;

public record CharacterEquipmentResponse(
    UUID inventoryItemId,
    String status,
    long stateVersion
) {
}
