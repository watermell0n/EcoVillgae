package com.b201.ecovillage.character.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
    name = "character_equipment",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_character_equipment_profile_slot",
            columnNames = {"character_profile_id", "slot"}
        ),
        @UniqueConstraint(
            name = "uq_character_equipment_inventory_item",
            columnNames = {"inventory_item_id"}
        )
    }
)
public class CharacterEquipment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "character_profile_id", nullable = false, updatable = false)
    private UUID characterProfileId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private CharacterSlot slot;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "equipped_at", nullable = false)
    private Instant equippedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CharacterEquipment() {
    }

    private CharacterEquipment(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID characterProfileId,
        UUID inventoryItemId,
        CharacterSlot slot,
        Instant equippedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.characterProfileId = Objects.requireNonNull(
            characterProfileId,
            "characterProfileId must not be null"
        );
        this.inventoryItemId = Objects.requireNonNull(
            inventoryItemId,
            "inventoryItemId must not be null"
        );
        this.slot = Objects.requireNonNull(slot, "slot must not be null");
        Instant now = Objects.requireNonNull(equippedAt, "equippedAt must not be null");
        this.equippedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CharacterEquipment equip(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID characterProfileId,
        UUID inventoryItemId,
        CharacterSlot slot,
        Instant equippedAt
    ) {
        return new CharacterEquipment(
            id,
            classroomId,
            classroomStudentId,
            characterProfileId,
            inventoryItemId,
            slot,
            equippedAt
        );
    }

    public void replaceWith(UUID nextInventoryItemId, Instant changedAt) {
        inventoryItemId = Objects.requireNonNull(
            nextInventoryItemId,
            "nextInventoryItemId must not be null"
        );
        Instant now = Objects.requireNonNull(changedAt, "changedAt must not be null");
        equippedAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public UUID getCharacterProfileId() {
        return characterProfileId;
    }

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public CharacterSlot getSlot() {
        return slot;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getEquippedAt() {
        return equippedAt;
    }
}
