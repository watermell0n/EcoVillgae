package com.b201.ecovillage.character.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

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
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;

/** 상점의 '저장된 모습 유지'가 쓰는 슬롯 일괄 저장 경로. */
class StudentCharacterProfileEquipmentTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    private final UUID classroomId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID hatItemId = UUID.randomUUID();
    private final UUID topItemId = UUID.randomUUID();
    private final UUID shoesItemId = UUID.randomUUID();
    private final UUID hatCatalogId = UUID.randomUUID();
    private final UUID topCatalogId = UUID.randomUUID();

    private final ClassroomStudentRepository studentRepository =
        mock(ClassroomStudentRepository.class);
    private final StudentCharacterProfileRepository profileRepository =
        mock(StudentCharacterProfileRepository.class);
    private final CharacterEquipmentRepository equipmentRepository =
        mock(CharacterEquipmentRepository.class);
    private final InventoryItemRepository inventoryRepository =
        mock(InventoryItemRepository.class);
    private final CharacterCatalogRepository catalogRepository =
        mock(CharacterCatalogRepository.class);
    private final StudentCharacterProfile profile = mock(StudentCharacterProfile.class);

    private StudentCharacterProfileService service() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(studentRepository.findActiveForUpdate(
            classroomId,
            studentId,
            ClassroomStudentStatus.ACTIVE
        )).thenReturn(Optional.of(
            ClassroomStudent.enroll(studentId, classroomId, 1, "착용학생", NOW)
        ));
        when(profileRepository.findByClassroomIdAndClassroomStudentId(classroomId, studentId))
            .thenReturn(Optional.of(profile));
        when(profile.getId()).thenReturn(profileId);
        when(profile.getClassroomId()).thenReturn(classroomId);
        when(profile.getClassroomStudentId()).thenReturn(studentId);
        when(profile.getEquipmentChangedAt()).thenReturn(NOW);
        when(profile.getCreatedAt()).thenReturn(NOW);
        when(profile.getUpdatedAt()).thenReturn(NOW);
        return new StudentCharacterProfileService(
            mock(ClassroomAuthorizationService.class),
            studentRepository,
            profileRepository,
            equipmentRepository,
            inventoryRepository,
            catalogRepository,
            new UuidV7Generator(clock, new SecureRandom()),
            clock
        );
    }

    private void ownItems() {
        when(inventoryRepository.findAllOwnedForUpdate(classroomId, studentId)).thenReturn(List.of(
            InventoryItem.acquire(hatItemId, classroomId, studentId, hatCatalogId, null, NOW),
            InventoryItem.acquire(topItemId, classroomId, studentId, topCatalogId, null, NOW)
        ));
        CharacterCatalog hat = mock(CharacterCatalog.class);
        when(hat.getCharacterCode()).thenReturn("hat_red_cap");
        CharacterCatalog top = mock(CharacterCatalog.class);
        when(top.getCharacterCode()).thenReturn("top_blue_tshirt");
        when(catalogRepository.findById(hatCatalogId)).thenReturn(Optional.of(hat));
        when(catalogRepository.findById(topCatalogId)).thenReturn(Optional.of(top));
    }

    private CharacterEquipment row(CharacterSlot slot, UUID inventoryItemId) {
        return CharacterEquipment.equip(
            UUID.randomUUID(),
            classroomId,
            studentId,
            profileId,
            inventoryItemId,
            slot,
            NOW
        );
    }

    @Test
    void savesEverySlotInOneCallAndDropsTheSlotsLeftEmpty() {
        ownItems();
        // 저장 전에는 신발만 착용 중이었다. 요청에 신발이 없으므로 해제 대상이다.
        CharacterEquipment shoes = row(CharacterSlot.SHOES, shoesItemId);
        CharacterEquipment hatRow = row(CharacterSlot.HAT, hatItemId);
        CharacterEquipment topRow = row(CharacterSlot.TOP, topItemId);
        when(equipmentRepository.findAllByClassroomIdAndCharacterProfileIdOrderBySlot(
            classroomId,
            profileId
        )).thenReturn(List.of(shoes), List.of(hatRow, topRow));
        when(profile.getStateVersion()).thenReturn(3L, 3L, 4L);
        List<CharacterEquipment> saved = new ArrayList<>();
        when(equipmentRepository.save(any(CharacterEquipment.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        Map<String, UUID> request = new LinkedHashMap<>();
        request.put("HAT", hatItemId);
        request.put("TOP", topItemId);
        request.put("SHOES", null);

        StudentCharacterProfileView view = service()
            .replaceEquipment(studentId.toString(), classroomId, 3L, request);

        verify(equipmentRepository).delete(shoes);
        assertEquals(2, saved.size());
        assertEquals(
            List.of(CharacterSlot.HAT, CharacterSlot.TOP),
            saved.stream().map(CharacterEquipment::getSlot).toList()
        );
        verify(profile).markEquipmentChanged(NOW);
        assertEquals(4L, view.stateVersion());
        assertEquals(
            Map.of("HAT", hatItemId, "TOP", topItemId),
            view.equippedInventoryItems()
        );

        // 유니크 제약(uq_character_equipment_inventory_item) 때문에 삭제를 먼저 flush해야 한다.
        InOrder order = inOrder(equipmentRepository);
        order.verify(equipmentRepository).delete(shoes);
        order.verify(equipmentRepository).flush();
        order.verify(equipmentRepository).save(any(CharacterEquipment.class));
    }

    @Test
    void keepsAnUnchangedSlotInsteadOfDeletingAndReinserting() {
        ownItems();
        CharacterEquipment hatRow = row(CharacterSlot.HAT, hatItemId);
        when(equipmentRepository.findAllByClassroomIdAndCharacterProfileIdOrderBySlot(
            classroomId,
            profileId
        )).thenReturn(List.of(hatRow), List.of(hatRow));
        when(profile.getStateVersion()).thenReturn(1L, 1L, 2L);

        service().replaceEquipment(
            studentId.toString(),
            classroomId,
            1L,
            Map.of("HAT", hatItemId)
        );

        verify(equipmentRepository, never()).delete(any(CharacterEquipment.class));
        verify(equipmentRepository, never()).save(any(CharacterEquipment.class));
        verify(profile).markEquipmentChanged(NOW);
    }

    @Test
    void rejectsAStaleProfileVersionBeforeTouchingAnySlot() {
        when(profile.getStateVersion()).thenReturn(7L);

        ClassroomOperationException failure = assertThrows(
            ClassroomOperationException.class,
            () -> service().replaceEquipment(
                studentId.toString(),
                classroomId,
                6L,
                Map.of("HAT", hatItemId)
            )
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
        assertEquals("STATE_VERSION_CONFLICT", failure.getCode());
        verify(inventoryRepository, never()).findAllOwnedForUpdate(any(), any());
        verify(equipmentRepository, never()).save(any(CharacterEquipment.class));
    }

    @Test
    void rejectsAnItemWhoseKindDoesNotMatchTheRequestedSlot() {
        ownItems();
        when(profile.getStateVersion()).thenReturn(0L);

        ClassroomOperationException failure = assertThrows(
            ClassroomOperationException.class,
            () -> service().replaceEquipment(
                studentId.toString(),
                classroomId,
                0L,
                Map.of("SHOES", hatItemId)
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("VALIDATION_ERROR", failure.getCode());
        verify(equipmentRepository, never()).save(any(CharacterEquipment.class));
    }

    @Test
    void rejectsAnItemTheStudentDoesNotOwn() {
        ownItems();
        when(profile.getStateVersion()).thenReturn(0L);

        ClassroomOperationException failure = assertThrows(
            ClassroomOperationException.class,
            () -> service().replaceEquipment(
                studentId.toString(),
                classroomId,
                0L,
                Map.of("HAT", UUID.randomUUID())
            )
        );

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatus());
        assertEquals("RESOURCE_NOT_FOUND", failure.getCode());
    }

    @Test
    void rejectsAnUnknownSlotName() {
        when(profile.getStateVersion()).thenReturn(0L);

        ClassroomOperationException failure = assertThrows(
            ClassroomOperationException.class,
            () -> service().replaceEquipment(
                studentId.toString(),
                classroomId,
                0L,
                Map.of("WINGS", hatItemId)
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("VALIDATION_ERROR", failure.getCode());
        verify(inventoryRepository, never()).findAllOwnedForUpdate(any(), any());
    }

    @Test
    void rejectsTheSameItemInTwoSlots() {
        ownItems();
        when(profile.getStateVersion()).thenReturn(0L);
        Map<String, UUID> request = new LinkedHashMap<>();
        request.put("HAT", hatItemId);
        request.put("TOP", hatItemId);

        ClassroomOperationException failure = assertThrows(
            ClassroomOperationException.class,
            () -> service().replaceEquipment(studentId.toString(), classroomId, 0L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("VALIDATION_ERROR", failure.getCode());
        assertEquals("같은 아이템을 두 슬롯에 착용할 수 없습니다.", failure.getMessage());
        verify(equipmentRepository, never()).save(any(CharacterEquipment.class));
    }
}
