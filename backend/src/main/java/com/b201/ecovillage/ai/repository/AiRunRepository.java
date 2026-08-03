package com.b201.ecovillage.ai.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.ai.domain.AiRun;

public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

    Optional<AiRun> findByClassroomIdAndId(UUID classroomId, UUID id);

    long countByRequestedByUserIdAndRunTypeAndRequestedAtGreaterThanEqual(
        UUID requestedByUserId,
        String runType,
        Instant from
    );

    boolean existsByClassroomIdAndStatus(UUID classroomId, String status);

    @Query("""
        select run
          from AiRun run
         where run.classroomId = :classroomId
           and (
               :cursorRequestedAt is null
               or run.requestedAt < :cursorRequestedAt
               or (run.requestedAt = :cursorRequestedAt and run.id < :cursorId)
           )
         order by run.requestedAt desc, run.id desc
        """)
    List<AiRun> findHistory(
        @Param("classroomId") UUID classroomId,
        @Param("cursorRequestedAt") Instant cursorRequestedAt,
        @Param("cursorId") UUID cursorId,
        Pageable pageable
    );
}
