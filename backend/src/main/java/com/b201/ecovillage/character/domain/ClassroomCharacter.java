package com.b201.ecovillage.character.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "classroom_characters")
public class ClassroomCharacter {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "character_catalog_id", nullable = false, updatable = false)
    private UUID characterCatalogId;

    @Column(name = "store_product_id", updatable = false)
    private UUID storeProductId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 40)
    private String status;

    protected ClassroomCharacter() {
    }

    public UUID getCharacterCatalogId() {
        return characterCatalogId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }
}
