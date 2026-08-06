package com.b201.ecovillage.character.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        return ok(view, correlationId);
    }

    /**
     * 캐릭터 착용 상태를 한 번에 저장한다. 상점의 '저장된 모습 유지'가 호출한다.
     *
     * 슬롯별 단건 명령(`POST /inventory-items/{R}/commands`)은 보관함의 즉시 착용을 위해
     * 그대로 둔다. 여러 슬롯을 함께 바꾸는 상점 저장은 요청이 슬롯 수만큼 늘고 중간 실패 시
     * 일부만 저장된 상태가 남기 때문에 이 경로를 쓴다.
     *
     * `expected_state_version`이 서버와 다르면 409를 돌려준다. 클라이언트는 드래프트를 버리지
     * 않고 최신 프로필을 다시 읽어 사용자에게 재확인을 요청한다.
     */
    @PutMapping("/equipment")
    public ResponseEntity<ApiResponse<StudentCharacterProfileResponse>> saveEquipment(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody CharacterEquipmentSaveRequest request,
        Principal principal
    ) {
        StudentCharacterProfileView view = profileService.replaceEquipment(
            principal == null ? null : principal.getName(),
            classroomId,
            request.expectedStateVersion(),
            request.equipment()
        );
        return ok(view, correlationId);
    }

    private ResponseEntity<ApiResponse<StudentCharacterProfileResponse>> ok(
        StudentCharacterProfileView view,
        UUID correlationId
    ) {
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
