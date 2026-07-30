package com.b201.bibim.domain.character;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CharacterCosmeticRepository extends JpaRepository<CharacterCosmeticEntity, UUID> {

    List<CharacterCosmeticEntity> findAllByRoomIdOrderByDisplayOrder(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cosmetic from CharacterCosmeticEntity cosmetic where cosmetic.id = :cosmeticId")
    Optional<CharacterCosmeticEntity> findByIdForUpdate(@Param("cosmeticId") UUID cosmeticId);
}
