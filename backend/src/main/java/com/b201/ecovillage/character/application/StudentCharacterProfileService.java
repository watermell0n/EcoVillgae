package com.b201.ecovillage.character.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.b201.ecovillage.character.repository.StudentCharacterProfileRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.domain.InventoryItemStatus;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;

@Service
public class StudentCharacterProfileService {

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomStudentRepository studentRepository;
    private final StudentCharacterProfileRepository profileRepository;
    private final CharacterEquipmentRepository equipmentRepository;
    private final InventoryItemRepository inventoryRepository;
    private final CharacterCatalogRepository catalogRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentCharacterProfileService(
        ClassroomAuthorizationService authorizationService,
        ClassroomStudentRepository studentRepository,
        StudentCharacterProfileRepository profileRepository,
        CharacterEquipmentRepository equipmentRepository,
        InventoryItemRepository inventoryRepository,
        CharacterCatalogRepository catalogRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.studentRepository = studentRepository;
        this.profileRepository = profileRepository;
        this.equipmentRepository = equipmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.catalogRepository = catalogRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StudentCharacterProfileView findMine(String subject, UUID classroomId) {
        UUID studentId = studentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
        return view(findOrCreate(classroomId, studentId));
    }

    /**
     * 슬롯 전체를 한 트랜잭션에서 교체한다. 상점의 '저장된 모습 유지'가 쓰는 경로다.
     *
     * 슬롯별 단건 명령을 반복하면 6슬롯 저장에 최대 6번의 요청이 나가고, 중간에 실패하면
     * 일부 슬롯만 저장된 상태가 남는다. 여기서는 프로필 단위 낙관적 락으로 한 번에 확정한다.
     *
     * @param equipment 슬롯 → 인벤토리 아이템. 전체 상태이며 값이 없는 슬롯은 해제한다.
     */
    @Transactional
    public StudentCharacterProfileView replaceEquipment(
        String subject,
        UUID classroomId,
        long expectedStateVersion,
        Map<String, UUID> equipment
    ) {
        UUID studentId = studentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
        StudentCharacterProfile profile = findOrCreate(classroomId, studentId);
        if (expectedStateVersion != profile.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "캐릭터 장착 상태가 변경되었습니다."
            );
        }

        Map<CharacterSlot, UUID> desired = parseSlots(equipment);
        Map<UUID, InventoryItem> owned = inventoryRepository
            .findAllOwnedForUpdate(classroomId, studentId)
            .stream()
            .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));
        validateDesired(desired, owned);

        Instant now = clock.instant();
        Map<CharacterSlot, CharacterEquipment> current = new EnumMap<>(CharacterSlot.class);
        for (CharacterEquipment row : equipmentRepository
            .findAllByClassroomIdAndCharacterProfileIdOrderBySlot(classroomId, profile.getId())) {
            current.put(row.getSlot(), row);
        }

        /*
         * 먼저 지우고 flush한 뒤에 새로 넣는다. character_equipment에는
         * uq_character_equipment_inventory_item 제약이 있어서, 같은 아이템이 다른 슬롯으로
         * 옮겨가는 경우 삭제와 삽입을 한 flush에 섞으면 유니크 위반이 날 수 있다.
         * slot 컬럼은 updatable = false라 행을 옮기는 것도 불가능하다.
         */
        List<CharacterEquipment> keep = new ArrayList<>();
        for (CharacterSlot slot : CharacterSlot.values()) {
            CharacterEquipment row = current.get(slot);
            if (row == null) continue;
            UUID target = desired.get(slot);
            if (target != null && target.equals(row.getInventoryItemId())) {
                keep.add(row);
                continue;
            }
            equipmentRepository.delete(row);
        }
        equipmentRepository.flush();

        for (Map.Entry<CharacterSlot, UUID> entry : desired.entrySet()) {
            boolean unchanged = keep.stream()
                .anyMatch(row -> row.getSlot() == entry.getKey());
            if (unchanged) continue;
            equipmentRepository.save(CharacterEquipment.equip(
                uuidGenerator.generate(),
                classroomId,
                studentId,
                profile.getId(),
                entry.getValue(),
                entry.getKey(),
                now
            ));
        }

        profile.markEquipmentChanged(now);
        equipmentRepository.flush();
        profileRepository.flush();
        return view(profile);
    }

    /** 요청의 슬롯 키를 검증해 enum으로 바꾼다. 값이 null인 슬롯은 해제이므로 버린다. */
    private static Map<CharacterSlot, UUID> parseSlots(Map<String, UUID> equipment) {
        Map<CharacterSlot, UUID> desired = new EnumMap<>(CharacterSlot.class);
        for (Map.Entry<String, UUID> entry : equipment.entrySet()) {
            if (entry.getValue() == null) continue;
            CharacterSlot slot;
            try {
                slot = CharacterSlot.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw operation(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "지원하지 않는 캐릭터 슬롯입니다: " + entry.getKey()
                );
            }
            desired.put(slot, entry.getValue());
        }
        return desired;
    }

    /** 소유·거래잠금·슬롯 일치·중복 착용을 저장 전에 모두 본다. */
    private void validateDesired(
        Map<CharacterSlot, UUID> desired,
        Map<UUID, InventoryItem> owned
    ) {
        // 구조 검사를 먼저 한다. DB 유니크 제약(inventory_item_id)에 맡기면 500이 된다.
        Map<UUID, CharacterSlot> seen = new HashMap<>();
        for (Map.Entry<CharacterSlot, UUID> entry : desired.entrySet()) {
            if (seen.putIfAbsent(entry.getValue(), entry.getKey()) != null) {
                throw operation(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "같은 아이템을 두 슬롯에 착용할 수 없습니다."
                );
            }
        }

        for (Map.Entry<CharacterSlot, UUID> entry : desired.entrySet()) {
            UUID itemId = entry.getValue();
            InventoryItem item = owned.get(itemId);
            if (item == null) {
                throw operation(
                    HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND",
                    "보유 아이템을 찾을 수 없습니다."
                );
            }
            if (item.getStatus() != InventoryItemStatus.AVAILABLE) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "ITEM_LOCKED",
                    "거래에 잠기지 않은 보유 아이템만 장착할 수 있습니다."
                );
            }
            CharacterCatalog character = catalogRepository
                .findById(item.getCharacterCatalogId())
                .orElseThrow(() -> operation(
                    HttpStatus.CONFLICT,
                    "CHARACTER_DATA_INCONSISTENT",
                    "캐릭터 카탈로그 정보를 찾을 수 없습니다."
                ));
            CharacterSlot itemSlot;
            try {
                itemSlot = CharacterSlot.fromCharacterCode(character.getCharacterCode());
            } catch (IllegalArgumentException exception) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "CHARACTER_DATA_INCONSISTENT",
                    "캐릭터 슬롯 정보를 확인할 수 없습니다."
                );
            }
            if (itemSlot != entry.getKey()) {
                throw operation(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "아이템 종류와 슬롯이 맞지 않습니다: " + character.getCharacterCode()
                );
            }
        }
    }

    @Transactional
    public StudentCharacterProfile findOrCreate(
        UUID classroomId,
        UUID classroomStudentId
    ) {
        ClassroomStudent student = activeStudent(classroomId, classroomStudentId);
        return profileRepository
            .findByClassroomIdAndClassroomStudentId(classroomId, classroomStudentId)
            .orElseGet(() -> createDefault(student));
    }

    private ClassroomStudent activeStudent(
        UUID classroomId,
        UUID classroomStudentId
    ) {
        return studentRepository.findActiveForUpdate(
            classroomId,
            classroomStudentId,
            ClassroomStudentStatus.ACTIVE
        )
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "캐릭터 프로필의 활성 학생을 찾을 수 없습니다."
            ));
    }

    private StudentCharacterProfile createDefault(ClassroomStudent student) {
        Instant now = clock.instant();
        return profileRepository.saveAndFlush(
            StudentCharacterProfile.createDefault(
                uuidGenerator.generate(),
                student.getClassroomId(),
                student.getId(),
                now
            )
        );
    }

    private StudentCharacterProfileView view(StudentCharacterProfile profile) {
        Map<String, UUID> equipped = new LinkedHashMap<>();
        for (CharacterEquipment item : equipmentRepository
            .findAllByClassroomIdAndCharacterProfileIdOrderBySlot(
                profile.getClassroomId(),
                profile.getId()
            )) {
            equipped.put(item.getSlot().name(), item.getInventoryItemId());
        }
        return new StudentCharacterProfileView(
            profile.getId(),
            profile.getClassroomStudentId(),
            profile.getStateVersion(),
            profile.getEquipmentChangedAt(),
            profile.getCreatedAt(),
            profile.getUpdatedAt(),
            Collections.unmodifiableMap(equipped)
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
