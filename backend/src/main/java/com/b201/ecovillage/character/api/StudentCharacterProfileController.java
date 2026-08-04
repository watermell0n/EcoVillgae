package com.b201.ecovillage.character.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.character.application.StudentCharacterProfileService;
import com.b201.ecovillage.character.application.StudentCharacterProfileView;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
@RequestMapping("/classrooms/{classroomId}/students/me/character-profile")
public class StudentCharacterProfileController {

    private final StudentCharacterProfileService profileService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentCharacterProfileController(
        StudentCharacterProfileService profileService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.profileService = profileService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StudentCharacterProfileResponse>> findMine(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        StudentCharacterProfileView view = profileService.findMine(
            principal == null ? null : principal.getName(),
            classroomId
        );
        StudentCharacterProfileResponse data = new StudentCharacterProfileResponse(
            view.profileId(),
            view.classroomStudentId(),
            view.stateVersion(),
            view.equipmentChangedAt(),
            view.createdAt(),
            view.updatedAt(),
            view.equippedInventoryItems()
        );
        return ResponseEntity.ok(new ApiResponse<>(
            data,
            new ApiMeta(
                uuidGenerator.generate(),
                correlationId == null ? uuidGenerator.generate() : correlationId,
                clock.instant()
            )
        ));
    }
}
