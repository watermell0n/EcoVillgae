package com.b201.ecovillage.vote.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.vote.domain.Vote;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByClassroomIdAndId(UUID classroomId, UUID id);

    Optional<Vote> findFirstByClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Vote> findForUpdateByClassroomIdAndId(UUID classroomId, UUID id);
}
