package com.b201.ecovillage.character.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InventoryEquipmentTest {

    private static final Instant ACQUIRED_AT = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void equipmentTracksSlotSeparatelyFromOwnedInventoryItem() {
        UUID firstItemId = UUID.randomUUID();
        CharacterEquipment equipment = equipment(firstItemId);

        assertEquals(CharacterSlot.TOP, equipment.getSlot());
        assertEquals(firstItemId, equipment.getInventoryItemId());

        UUID replacementId = UUID.randomUUID();
        equipment.replaceWith(replacementId, ACQUIRED_AT.plusSeconds(1));
        assertEquals(replacementId, equipment.getInventoryItemId());
        assertEquals(ACQUIRED_AT.plusSeconds(1), equipment.getEquippedAt());
    }

    @Test
    void characterCodePrefixMapsToATypeSafeSlot() {
        assertEquals(CharacterSlot.HAT, CharacterSlot.fromCharacterCode("HAT_RED_CAP"));
        assertEquals(CharacterSlot.BACKGROUND, CharacterSlot.fromCharacterCode("bg_snow"));
    }

    private static CharacterEquipment equipment(UUID inventoryItemId) {
        return CharacterEquipment.equip(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            inventoryItemId,
            CharacterSlot.TOP,
            ACQUIRED_AT
        );
    }
}
