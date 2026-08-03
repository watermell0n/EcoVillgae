package com.b201.ecovillage.character.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.character.domain.CharacterEquipment;
import com.b201.ecovillage.character.domain.CharacterSlot;
import com.b201.ecovillage.character.domain.StudentCharacterProfile;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:character-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class CharacterProfilePersistenceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired
    private StudentCharacterProfileRepository profileRepository;

    @Autowired
    private CharacterEquipmentRepository equipmentRepository;

    @Test
    void oneStudentCannotHaveTwoProfiles() {
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        profileRepository.saveAndFlush(profile(classroomId, studentId));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> profileRepository.saveAndFlush(profile(classroomId, studentId))
        );
    }

    @Test
    void equipmentReplacementHasItsOwnOptimisticVersion() {
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentCharacterProfile profile = profileRepository.saveAndFlush(
            profile(classroomId, studentId)
        );
        CharacterEquipment equipment = equipmentRepository.saveAndFlush(equipment(
            classroomId,
            studentId,
            profile.getId(),
            UUID.randomUUID(),
            CharacterSlot.TOP
        ));
        assertEquals(0, equipment.getStateVersion());

        equipment.replaceWith(UUID.randomUUID(), NOW.plusSeconds(1));
        equipmentRepository.flush();

        assertEquals(1, equipment.getStateVersion());
    }

    @Test
    void profileVersionAdvancesForEquipmentChangesEvenAtTheSameTimestamp() {
        StudentCharacterProfile profile = profileRepository.saveAndFlush(profile(
            UUID.randomUUID(),
            UUID.randomUUID()
        ));
        assertEquals(0, profile.getStateVersion());

        profile.markEquipmentChanged(NOW);
        profileRepository.flush();

        assertEquals(1, profile.getStateVersion());
        assertEquals(1, profile.getEquipmentRevision());
    }

    @Test
    void oneSlotAndOneInventoryItemHaveOnlyOneActiveAssignment() {
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentCharacterProfile profile = profileRepository.saveAndFlush(
            profile(classroomId, studentId)
        );
        UUID inventoryItemId = UUID.randomUUID();
        equipmentRepository.saveAndFlush(equipment(
            classroomId,
            studentId,
            profile.getId(),
            inventoryItemId,
            CharacterSlot.HAT
        ));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> equipmentRepository.saveAndFlush(equipment(
                classroomId,
                studentId,
                profile.getId(),
                inventoryItemId,
                CharacterSlot.TOP
            ))
        );
    }

    @Test
    void differentSlotsRemainEquippedAtTheSameTime() {
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentCharacterProfile profile = profileRepository.saveAndFlush(
            profile(classroomId, studentId)
        );

        equipmentRepository.save(equipment(
            classroomId,
            studentId,
            profile.getId(),
            UUID.randomUUID(),
            CharacterSlot.HAT
        ));
        equipmentRepository.saveAndFlush(equipment(
            classroomId,
            studentId,
            profile.getId(),
            UUID.randomUUID(),
            CharacterSlot.SHOES
        ));

        var equipped = equipmentRepository
            .findAllByClassroomIdAndCharacterProfileIdOrderBySlot(
                classroomId,
                profile.getId()
            );
        assertEquals(2, equipped.size());
        assertTrue(equipped.stream().anyMatch(item -> item.getSlot() == CharacterSlot.HAT));
        assertTrue(equipped.stream().anyMatch(item -> item.getSlot() == CharacterSlot.SHOES));
    }

    private static StudentCharacterProfile profile(UUID classroomId, UUID studentId) {
        return StudentCharacterProfile.createDefault(
            UUID.randomUUID(),
            classroomId,
            studentId,
            NOW
        );
    }

    private static CharacterEquipment equipment(
        UUID classroomId,
        UUID studentId,
        UUID profileId,
        UUID inventoryItemId,
        CharacterSlot slot
    ) {
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
}
