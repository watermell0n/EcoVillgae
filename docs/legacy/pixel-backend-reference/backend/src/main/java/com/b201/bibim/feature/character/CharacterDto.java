package com.b201.bibim.feature.character;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CharacterDto {

    private CharacterDto() {
    }

    public record CosmeticItemResponse(
            UUID cosmeticId,
            String name,
            String slot,
            long price,
            String spriteKey,
            String theme,
            Map<String, Object> anchor,
            int displayOrder,
            String status,
            boolean owned,
            boolean equipped
    ) {
    }

    public record CharacterOverviewResponse(
            UUID characterId,
            String name,
            long walletBalance,
            String bodyState,
            Map<String, Object> equipped,
            List<CosmeticItemResponse> cosmetics
    ) {
    }

    public record CharacterResponse(
            UUID characterId,
            String name,
            String bodyState,
            Map<String, Object> equipped
    ) {
    }

    public record PurchaseRequest(
            @NotNull UUID cosmeticId
    ) {
    }

    public record PurchaseResponse(
            UUID ownershipId,
            UUID cosmeticId,
            String cosmeticName,
            String slot,
            long pricePaid,
            long walletBalance,
            String bodyState,
            boolean replayed
    ) {
    }

    public record EquipRequest(
            @NotBlank String slot,
            UUID cosmeticId
    ) {
    }

    public record RenameRequest(
            @NotBlank @Size(max = 20) String name
    ) {
    }
}
