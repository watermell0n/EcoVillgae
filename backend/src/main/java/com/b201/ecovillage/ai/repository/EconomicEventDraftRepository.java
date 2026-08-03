package com.b201.ecovillage.ai.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.ai.domain.EconomicEventDraft;

public interface EconomicEventDraftRepository extends JpaRepository<EconomicEventDraft, UUID> {

    Optional<EconomicEventDraft> findByClassroomIdAndId(UUID classroomId, UUID id);

    boolean existsByClassroomIdAndSourceProposalId(UUID classroomId, UUID sourceProposalId);
}
