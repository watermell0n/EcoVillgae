package com.b201.ecovillage.ai.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.ai.domain.AiProposal;

public interface AiProposalRepository extends JpaRepository<AiProposal, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiProposal> findByClassroomIdAndId(UUID classroomId, UUID id);

    Optional<AiProposal> findByClassroomIdAndDedupeKey(UUID classroomId, String dedupeKey);
}
