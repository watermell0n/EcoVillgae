package com.b201.ecovillage.character.api;

import java.util.List;

public record CharacterInventoryListResponse(
    List<CharacterInventoryResponse> inventoryItems
) {
}
