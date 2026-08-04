package com.b201.bibim.feature.character;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.character.CharacterDto.CharacterOverviewResponse;
import com.b201.bibim.feature.character.CharacterDto.CharacterResponse;
import com.b201.bibim.feature.character.CharacterDto.EquipRequest;
import com.b201.bibim.feature.character.CharacterDto.PurchaseRequest;
import com.b201.bibim.feature.character.CharacterDto.PurchaseResponse;
import com.b201.bibim.feature.character.CharacterDto.RenameRequest;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/character")
public class StudentCharacterController {

    private final CharacterService characterService;

    public StudentCharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping
    ApiResponse<CharacterOverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                characterService.overview(principal.id()),
                requestId(request)
        );
    }

    @PostMapping("/cosmetics/purchases")
    ApiResponse<PurchaseResponse> purchase(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseRequest purchaseRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                characterService.purchase(principal.id(), idempotencyKey, purchaseRequest),
                requestId(request)
        );
    }

    @PatchMapping("/equipment")
    ApiResponse<CharacterResponse> equip(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody EquipRequest equipRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                characterService.equip(principal.id(), equipRequest),
                requestId(request)
        );
    }

    @PatchMapping("/name")
    ApiResponse<CharacterResponse> rename(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody RenameRequest renameRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                characterService.rename(principal.id(), renameRequest),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
