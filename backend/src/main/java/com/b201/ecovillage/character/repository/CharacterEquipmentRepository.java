package com.b201.ecovillage.character.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.character.domain.CharacterEquipment;
import com.b201.ecovillage.character.domain.CharacterSlot;

public interface CharacterEquipmentRepository extends JpaRepository<CharacterEquipment, UUID> {

    List<CharacterEquipment> findAllByClassroomIdAndCharacterProfileIdOrderBySlot(
        UUID classroomId,
        UUID characterProfileId
    );

    Optional<CharacterEquipment> findByClassroomIdAndCharacterProfileIdAndSlot(
        UUID classroomId,
        UUID characterProfileId,
        CharacterSlot slot
    );

    Optional<CharacterEquipment> findByClassroomIdAndCharacterProfileIdAndInventoryItemId(
        UUID classroomId,
        UUID characterProfileId,
        UUID inventoryItemId
    );

    boolean existsByClassroomIdAndInventoryItemIdIn(
        UUID classroomId,
        Collection<UUID> inventoryItemIds
    );
}
