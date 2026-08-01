package com.b201.ecovillage.character.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "character_catalog")
public class CharacterCatalog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "character_code", nullable = false, updatable = false, length = 80)
    private String characterCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String status;

    protected CharacterCatalog() {
    }

    public UUID getId() {
        return id;
    }

    public String getCharacterCode() {
        return characterCode;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }
}
