package com.b201.bibim.domain.vote;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteSessionRepository extends JpaRepository<VoteSessionEntity, UUID> {

    Optional<VoteSessionEntity> findByRoomId(UUID roomId);

    boolean existsByRoomIdAndStatus(UUID roomId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from VoteSessionEntity session where session.id = :id")
    Optional<VoteSessionEntity> findByIdForUpdate(@Param("id") UUID id);
}

