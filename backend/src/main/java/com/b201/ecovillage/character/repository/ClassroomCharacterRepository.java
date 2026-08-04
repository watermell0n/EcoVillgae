package com.b201.ecovillage.character.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.character.domain.ClassroomCharacter;

public interface ClassroomCharacterRepository
    extends JpaRepository<ClassroomCharacter, UUID> {

    Optional<ClassroomCharacter> findByClassroomIdAndStoreProductId(
        UUID classroomId,
        UUID storeProductId
    );

    Optional<ClassroomCharacter> findByClassroomIdAndId(
        UUID classroomId,
        UUID id
    );

    Optional<ClassroomCharacter> findByClassroomIdAndCharacterCatalogId(
        UUID classroomId,
        UUID characterCatalogId
    );
}
