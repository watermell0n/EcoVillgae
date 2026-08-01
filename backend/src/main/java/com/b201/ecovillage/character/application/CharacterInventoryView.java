package com.b201.ecovillage.character.application;

import java.util.UUID;

public record CharacterInventoryView(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    String characterCode,
    boolean equipped
) {
}
