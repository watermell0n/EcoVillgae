package com.b201.bibim.domain.character;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterCosmeticOwnershipRepository
        extends JpaRepository<CharacterCosmeticOwnershipEntity, UUID> {

    List<CharacterCosmeticOwnershipEntity> findAllByCharacterId(UUID characterId);

    Optional<CharacterCosmeticOwnershipEntity> findByCharacterIdAndCosmeticId(
            UUID characterId,
            UUID cosmeticId
    );

    Optional<CharacterCosmeticOwnershipEntity> findByTransactionId(UUID transactionId);
}
