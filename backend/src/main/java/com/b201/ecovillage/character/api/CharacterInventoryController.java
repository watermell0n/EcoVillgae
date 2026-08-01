package com.b201.ecovillage.character.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.character.application.CharacterInventoryCommandResult;
import com.b201.ecovillage.character.application.CharacterInventoryService;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
@RequestMapping("/classrooms/{classroomId}")
public class CharacterInventoryController {

    private final CharacterInventoryService inventoryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public CharacterInventoryController(
        CharacterInventoryService inventoryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.inventoryService = inventoryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/students/me/inventory")
    public ResponseEntity<ApiResponse<CharacterInventoryListResponse>> findMine(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        CharacterInventoryListResponse data = new CharacterInventoryListResponse(
            inventoryService.findMine(subject(principal), classroomId)
                .stream()
                .map(view -> new CharacterInventoryResponse(
                    view.resourceId(),
                    view.label(),
                    view.status(),
                    view.version(),
                    view.stateVersion(),
                    view.characterCode(),
                    view.equipped()
                ))
                .toList()
        );
        return ResponseEntity.ok(new ApiResponse<>(
            data,
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        ));
    }

    @PostMapping("/inventory-items/{inventoryItemId}/commands")
    public ResponseEntity<ApiResponse<CharacterEquipmentResponse>> changeEquipment(
        @PathVariable UUID classroomId,
        @PathVariable UUID inventoryItemId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody CharacterEquipmentRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        CharacterInventoryCommandResult result = inventoryService.changeEquipment(
            subject(principal),
            classroomId,
            inventoryItemId,
            request.expectedStateVersion(),
            request.command()
        );
        CharacterEquipmentResponse data = new CharacterEquipmentResponse(
            result.inventoryItemId(),
            result.status(),
            result.stateVersion()
        );
        return ResponseEntity.ok(new ApiResponse<>(
            data,
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        ));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
