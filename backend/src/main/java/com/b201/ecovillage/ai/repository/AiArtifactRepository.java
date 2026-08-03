package com.b201.ecovillage.ai.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.ai.domain.AiArtifact;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, UUID> {

    List<AiArtifact> findAllByAiRunIdInOrderByCreatedAtDesc(Collection<UUID> aiRunIds);
}
