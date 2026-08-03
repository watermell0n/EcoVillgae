package com.b201.ecovillage.character.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.character.domain.CharacterCatalog;
import com.b201.ecovillage.character.domain.CharacterEquipment;
import com.b201.ecovillage.character.domain.CharacterSlot;
import com.b201.ecovillage.character.domain.StudentCharacterProfile;
import com.b201.ecovillage.character.repository.CharacterCatalogRepository;
import com.b201.ecovillage.character.repository.CharacterEquipmentRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.domain.InventoryItemStatus;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;

@Service
public class CharacterInventoryService {

    private static final String EQUIPMENT_ROUTE =
        "/classrooms/{classroom_id}/inventory-items/{inventory_item_id}/commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final InventoryItemRepository inventoryRepository;
    private final CharacterCatalogRepository catalogRepository;
    private final CharacterEquipmentRepository equipmentRepository;
    private final StudentCharacterProfileService profileService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public CharacterInventoryService(
        ClassroomAuthorizationService authorizationService,
        InventoryItemRepository inventoryRepository,
        CharacterCatalogRepository catalogRepository,
        CharacterEquipmentRepository equipmentRepository,
        StudentCharacterProfileService profileService,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.inventoryRepository = inventoryRepository;
        this.catalogRepository = catalogRepository;
        this.equipmentRepository = equipmentRepository;
        this.profileService = profileService;
        this.idempotencyRepository = idempotencyRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public List<CharacterInventoryView> findMine(String subject, UUID classroomId) {
        UUID studentId = studentId(subject);
        authorize(subject, classroomId, studentId);
        StudentCharacterProfile profile = profileService.findOrCreate(classroomId, studentId);
        List<InventoryItem> items = inventoryRepository
            .findAllOwnedForUpdate(classroomId, studentId)
            .stream()
            .sorted(
                Comparator.comparing(InventoryItem::getAcquiredAt)
                    .reversed()
                    .thenComparing(InventoryItem::getId)
            )
            .toList();
        Map<UUID, CharacterCatalog> catalog = catalogRepository
            .findAllById(items.stream().map(InventoryItem::getCharacterCatalogId).toList())
            .stream()
            .collect(Collectors.toMap(CharacterCatalog::getId, Function.identity()));
        Set<UUID> equippedItemIds = equipmentRepository
            .findAllByClassroomIdAndCharacterProfileIdOrderBySlot(
                classroomId,
                profile.getId()
            )
            .stream()
            .map(CharacterEquipment::getInventoryItemId)
            .collect(Collectors.toSet());
        return items.stream()
            .map(item -> {
                CharacterCatalog character = catalog.get(item.getCharacterCatalogId());
                if (character == null) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "CHARACTER_DATA_INCONSISTENT",
                        "캐릭터 카탈로그 정보를 찾을 수 없습니다."
                    );
                }
                boolean equipped = equippedItemIds.contains(item.getId());
                return new CharacterInventoryView(
                    item.getId(),
                    character.getName(),
                    equipped ? "EQUIPPED" : item.getStatus().name(),
                    1,
                    profile.getStateVersion(),
                    character.getCharacterCode(),
                    equipped
                );
            })
            .toList();
    }

    @Transactional
    public CharacterInventoryCommandResult changeEquipment(
        String subject,
        UUID classroomId,
        UUID inventoryItemId,
        UUID requestId,
        long expectedStateVersion,
        String command
    ) {
        UUID studentId = studentId(subject);
        authorize(subject, classroomId, studentId);
        StudentCharacterProfile profile = profileService.findOrCreate(classroomId, studentId);
        String requestHash = equipmentRequestHash(
            classroomId,
            inventoryItemId,
            expectedStateVersion,
            command
        );
        CharacterInventoryCommandResult replay = replayEquipment(
            classroomId,
            studentId,
            requestId,
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        List<InventoryItem> owned = inventoryRepository.findAllOwnedForUpdate(
            classroomId,
            studentId
        );
        InventoryItem target = owned.stream()
            .filter(item -> item.getId().equals(inventoryItemId))
            .findFirst()
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "보유 아이템을 찾을 수 없습니다."
            ));
        Instant now = clock.instant();
        if (expectedStateVersion != profile.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "캐릭터 장착 상태가 변경되었습니다."
            );
        }
        if ("UNEQUIP".equals(command)) {
            CharacterEquipment equipped = equipmentRepository
                .findByClassroomIdAndCharacterProfileIdAndInventoryItemId(
                    classroomId,
                    profile.getId(),
                    target.getId()
                )
                .orElseThrow(() -> operation(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "장착 중인 아이템만 해제할 수 있습니다."
                ));
            equipmentRepository.delete(equipped);
        } else if ("EQUIP".equals(command)) {
            CharacterSlot targetSlot;
            try {
                targetSlot = CharacterSlot.fromCharacterCode(
                    catalog(target).getCharacterCode()
                );
            } catch (IllegalArgumentException exception) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "CHARACTER_DATA_INCONSISTENT",
                    "캐릭터 슬롯 정보를 확인할 수 없습니다."
                );
            }
            if (target.getStatus() != InventoryItemStatus.AVAILABLE) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "ITEM_LOCKED",
                    "거래에 잠기지 않은 보유 아이템만 장착할 수 있습니다."
                );
            }
            if (equipmentRepository
                .findByClassroomIdAndCharacterProfileIdAndInventoryItemId(
                    classroomId,
                    profile.getId(),
                    target.getId()
                )
                .isPresent()) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "이미 장착 중인 아이템입니다."
                );
            }
            CharacterEquipment equipped = equipmentRepository
                .findByClassroomIdAndCharacterProfileIdAndSlot(
                    classroomId,
                    profile.getId(),
                    targetSlot
                )
                .orElseGet(() -> CharacterEquipment.equip(
                    uuidGenerator.generate(),
                    classroomId,
                    studentId,
                    profile.getId(),
                    target.getId(),
                    targetSlot,
                    now
                ));
            if (!equipped.getInventoryItemId().equals(target.getId())) {
                equipped.replaceWith(target.getId(), now);
            }
            equipmentRepository.save(equipped);
        } else {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "command는 EQUIP 또는 UNEQUIP이어야 합니다."
            );
        }

        profile.markEquipmentChanged(now);
        equipmentRepository.flush();
        CharacterInventoryCommandResult result = new CharacterInventoryCommandResult(
            target.getId(),
            "EQUIP".equals(command) ? "EQUIPPED" : target.getStatus().name(),
            profile.getStateVersion()
        );
        rememberEquipment(
            classroomId,
            studentId,
            requestId,
            requestHash,
            result,
            now
        );
        return result;
    }

    private CharacterInventoryCommandResult replayEquipment(
        UUID classroomId,
        UUID studentId,
        UUID requestId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository.findForReplay(
            classroomId,
            studentId,
            EQUIPMENT_ROUTE,
            requestId.toString()
        ).orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Map<String, Object> body = record.getResponseBody();
        try {
            return new CharacterInventoryCommandResult(
                UUID.fromString(body.get("inventory_item_id").toString()),
                body.get("status").toString(),
                ((Number) body.get("state_version")).longValue()
            );
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "이전 캐릭터 장착 결과를 확인할 수 없습니다."
            );
        }
    }

    private void rememberEquipment(
        UUID classroomId,
        UUID studentId,
        UUID requestId,
        String requestHash,
        CharacterInventoryCommandResult result,
        Instant now
    ) {
        idempotencyRepository.save(IdempotencyRecord.completed(
            uuidGenerator.generate(),
            classroomId,
            studentId,
            EQUIPMENT_ROUTE,
            requestId.toString(),
            requestHash,
            HttpStatus.OK.value(),
            Map.of(
                "inventory_item_id", result.inventoryItemId().toString(),
                "status", result.status(),
                "state_version", result.stateVersion()
            ),
            null,
            now.plus(IDEMPOTENCY_TTL),
            now
        ));
    }

    private static String equipmentRequestHash(
        UUID classroomId,
        UUID inventoryItemId,
        long expectedStateVersion,
        String command
    ) {
        return sha256(
            classroomId + "|" + inventoryItemId + "|" + expectedStateVersion + "|" + command
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the equipment command.", exception);
        }
    }

    private CharacterCatalog catalog(InventoryItem item) {
        return catalogRepository.findById(item.getCharacterCatalogId())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "CHARACTER_DATA_INCONSISTENT",
                "캐릭터 카탈로그 정보를 찾을 수 없습니다."
            ));
    }

    private void authorize(String subject, UUID classroomId, UUID studentId) {
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "학생 인증 정보를 확인할 수 없습니다."
            );
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
