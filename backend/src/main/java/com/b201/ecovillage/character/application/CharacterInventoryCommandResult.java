package com.b201.ecovillage.character.application;

import java.util.UUID;

public record CharacterInventoryCommandResult(
    UUID inventoryItemId,
    String status,
    long stateVersion
) {
}
