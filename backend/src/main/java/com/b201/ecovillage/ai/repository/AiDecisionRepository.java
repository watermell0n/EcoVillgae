package com.b201.ecovillage.ai.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.ai.domain.AiDecision;

public interface AiDecisionRepository extends JpaRepository<AiDecision, UUID> {

    boolean existsByAiProposalIdAndDecision(UUID proposalId, String decision);
}
