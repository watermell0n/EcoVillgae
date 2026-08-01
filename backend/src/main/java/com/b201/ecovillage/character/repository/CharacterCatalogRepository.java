package com.b201.ecovillage.character.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.character.domain.CharacterCatalog;

public interface CharacterCatalogRepository extends JpaRepository<CharacterCatalog, UUID> {
}
