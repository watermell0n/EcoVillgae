package com.b201.ecovillage.character.application;

import java.util.UUID;

/** stateVersion is the character profile version after the equipment command. */
public record CharacterInventoryCommandResult(
    UUID inventoryItemId,
    String status,
    long stateVersion
) {
}
