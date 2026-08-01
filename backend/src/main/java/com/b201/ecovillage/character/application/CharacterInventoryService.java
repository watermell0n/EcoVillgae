package com.b201.ecovillage.character.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.character.domain.CharacterCatalog;
import com.b201.ecovillage.character.repository.CharacterCatalogRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.domain.InventoryItemStatus;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;

@Service
public class CharacterInventoryService {

    private final ClassroomAuthorizationService authorizationService;
    private final InventoryItemRepository inventoryRepository;
    private final CharacterCatalogRepository catalogRepository;
    private final Clock clock;

    public CharacterInventoryService(
        ClassroomAuthorizationService authorizationService,
        InventoryItemRepository inventoryRepository,
        CharacterCatalogRepository catalogRepository,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.inventoryRepository = inventoryRepository;
        this.catalogRepository = catalogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CharacterInventoryView> findMine(String subject, UUID classroomId) {
        UUID studentId = studentId(subject);
        authorize(subject, classroomId, studentId);
        List<InventoryItem> items =
            inventoryRepository.findAllByClassroomIdAndClassroomStudentIdOrderByAcquiredAtDesc(
                classroomId,
                studentId
            );
        Map<UUID, CharacterCatalog> catalog = catalogRepository
            .findAllById(items.stream().map(InventoryItem::getCharacterCatalogId).toList())
            .stream()
            .collect(Collectors.toMap(CharacterCatalog::getId, Function.identity()));
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
                return new CharacterInventoryView(
                    item.getId(),
                    character.getName(),
                    item.getStatus().name(),
                    1,
                    item.getStateVersion(),
                    character.getCharacterCode(),
                    item.getStatus() == InventoryItemStatus.EQUIPPED
                );
            })
            .toList();
    }

    @Transactional
    public CharacterInventoryCommandResult changeEquipment(
        String subject,
        UUID classroomId,
        UUID inventoryItemId,
        Long expectedStateVersion,
        String command
    ) {
        UUID studentId = studentId(subject);
        authorize(subject, classroomId, studentId);
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
        if (
            expectedStateVersion != null
                && expectedStateVersion != target.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "아이템 상태가 변경되었습니다."
            );
        }
        Instant now = clock.instant();

        if ("UNEQUIP".equals(command)) {
            target.unequip(studentId, now);
        } else if ("EQUIP".equals(command)) {
            String targetSlot = slotOf(catalog(target).getCharacterCode());
            for (InventoryItem item : owned) {
                if (
                    item.getStatus() == InventoryItemStatus.EQUIPPED
                        && !item.getId().equals(target.getId())
                        && slotOf(catalog(item).getCharacterCode()).equals(targetSlot)
                ) {
                    item.unequip(studentId, now);
                }
            }
            target.equip(studentId, now);
        } else {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "command는 EQUIP 또는 UNEQUIP이어야 합니다."
            );
        }

        inventoryRepository.saveAll(owned);
        return new CharacterInventoryCommandResult(
            target.getId(),
            target.getStatus().name(),
            target.getStateVersion()
        );
    }

    private CharacterCatalog catalog(InventoryItem item) {
        return catalogRepository.findById(item.getCharacterCatalogId())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "CHARACTER_DATA_INCONSISTENT",
                "캐릭터 카탈로그 정보를 찾을 수 없습니다."
            ));
    }

    private static String slotOf(String characterCode) {
        int separator = characterCode.indexOf('_');
        return separator < 0 ? characterCode : characterCode.substring(0, separator);
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
