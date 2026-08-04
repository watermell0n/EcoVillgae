package com.b201.ecovillage.character.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StudentCharacterProfileView(
    UUID profileId,
    UUID classroomStudentId,
    long stateVersion,
    Instant equipmentChangedAt,
    Instant createdAt,
    Instant updatedAt,
    Map<String, UUID> equippedInventoryItems
) {
}
