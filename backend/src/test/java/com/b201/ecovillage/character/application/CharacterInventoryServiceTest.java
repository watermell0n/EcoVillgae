package com.b201.ecovillage.character.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.b201.ecovillage.character.domain.CharacterCatalog;
import com.b201.ecovillage.character.domain.CharacterEquipment;
import com.b201.ecovillage.character.domain.CharacterSlot;
import com.b201.ecovillage.character.domain.StudentCharacterProfile;
import com.b201.ecovillage.character.repository.CharacterCatalogRepository;
import com.b201.ecovillage.character.repository.CharacterEquipmentRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;

class CharacterInventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void equipmentCommandUsesTheProfileVersionAndReplaysTheSameRequest() {
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        InventoryItemRepository inventoryRepository = mock(InventoryItemRepository.class);
        CharacterCatalogRepository catalogRepository = mock(CharacterCatalogRepository.class);
        CharacterEquipmentRepository equipmentRepository =
            mock(CharacterEquipmentRepository.class);
        StudentCharacterProfileService profileService =
            mock(StudentCharacterProfileService.class);
        IdempotencyRecordRepository idempotencyRepository =
            mock(IdempotencyRecordRepository.class);
        StudentCharacterProfile profile = mock(StudentCharacterProfile.class);
        CharacterCatalog catalog = mock(CharacterCatalog.class);
        InventoryItem item = InventoryItem.acquire(
            itemId,
            classroomId,
            studentId,
            catalogId,
            null,
            NOW
        );

        when(inventoryRepository.findAllOwnedForUpdate(classroomId, studentId))
            .thenReturn(List.of(item));
        when(profileService.findOrCreate(classroomId, studentId)).thenReturn(profile);
        when(profile.getId()).thenReturn(profileId);
        // Before the command and after the flush, respectively.
        when(profile.getStateVersion()).thenReturn(4L, 5L);
        when(catalogRepository.findById(catalogId)).thenReturn(Optional.of(catalog));
        when(catalog.getCharacterCode()).thenReturn("HAT_RED_CAP");
        when(equipmentRepository
            .findByClassroomIdAndCharacterProfileIdAndInventoryItemId(
                classroomId,
                profileId,
                itemId
            ))
            .thenReturn(Optional.empty());
        when(equipmentRepository.findByClassroomIdAndCharacterProfileIdAndSlot(
            classroomId,
            profileId,
            CharacterSlot.HAT
        )).thenReturn(Optional.empty());
        when(equipmentRepository.save(any(CharacterEquipment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        AtomicReference<IdempotencyRecord> storedReplay = new AtomicReference<>();
        when(idempotencyRepository.findForReplay(
            classroomId,
            studentId,
            "/classrooms/{classroom_id}/inventory-items/{inventory_item_id}/commands",
            requestId.toString()
        )).thenAnswer(invocation -> Optional.ofNullable(storedReplay.get()));
        when(idempotencyRepository.save(any(IdempotencyRecord.class)))
            .thenAnswer(invocation -> {
                IdempotencyRecord record = invocation.getArgument(0);
                storedReplay.set(record);
                return record;
            });

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CharacterInventoryService service = new CharacterInventoryService(
            mock(ClassroomAuthorizationService.class),
            inventoryRepository,
            catalogRepository,
            equipmentRepository,
            profileService,
            idempotencyRepository,
            new UuidV7Generator(clock, new SecureRandom()),
            clock
        );

        CharacterInventoryCommandResult result = service.changeEquipment(
            studentId.toString(),
            classroomId,
            itemId,
            requestId,
            4L,
            "EQUIP"
        );

        assertEquals("EQUIPPED", result.status());
        assertEquals(5L, result.stateVersion());
        CharacterInventoryCommandResult replay = service.changeEquipment(
            studentId.toString(),
            classroomId,
            itemId,
            requestId,
            4L,
            "EQUIP"
        );
        assertEquals(result, replay);
        InOrder lockOrder = inOrder(profileService, inventoryRepository);
        lockOrder.verify(profileService).findOrCreate(classroomId, studentId);
        lockOrder.verify(inventoryRepository).findAllOwnedForUpdate(classroomId, studentId);
        verify(inventoryRepository).findAllOwnedForUpdate(classroomId, studentId);
        verify(profile).markEquipmentChanged(NOW);
        verify(equipmentRepository).flush();
        verify(idempotencyRepository).save(any(IdempotencyRecord.class));
    }
}
