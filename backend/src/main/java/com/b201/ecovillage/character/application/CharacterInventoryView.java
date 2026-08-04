package com.b201.ecovillage.character.application;

import java.util.UUID;

/**
 * stateVersion is the owning character profile version. All rows from one inventory response
 * therefore carry the same equipment concurrency token.
 */
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
