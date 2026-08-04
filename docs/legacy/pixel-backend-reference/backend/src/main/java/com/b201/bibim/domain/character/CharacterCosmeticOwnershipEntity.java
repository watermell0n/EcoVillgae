package com.b201.bibim.domain.character;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "character_cosmetic_ownership")
public class CharacterCosmeticOwnershipEntity {

    @Id
    private UUID id;

    @Column(name = "character_id", nullable = false)
    private UUID characterId;

    @Column(name = "cosmetic_id", nullable = false)
    private UUID cosmeticId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "price_paid", nullable = false)
    private long pricePaid;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CharacterCosmeticOwnershipEntity() {
    }

    public static CharacterCosmeticOwnershipEntity create(
            UUID characterId,
            UUID cosmeticId,
            UUID transactionId,
            long pricePaid
    ) {
        CharacterCosmeticOwnershipEntity ownership = new CharacterCosmeticOwnershipEntity();
        ownership.id = UUID.randomUUID();
        ownership.characterId = characterId;
        ownership.cosmeticId = cosmeticId;
        ownership.transactionId = transactionId;
        ownership.pricePaid = pricePaid;
        ownership.createdAt = Instant.now();
        return ownership;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public UUID getCosmeticId() {
        return cosmeticId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public long getPricePaid() {
        return pricePaid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
