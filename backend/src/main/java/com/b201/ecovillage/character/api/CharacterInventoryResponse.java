package com.b201.ecovillage.character.api;

import java.util.UUID;

public record CharacterInventoryResponse(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    String characterCode,
    boolean equipped
) {
}
