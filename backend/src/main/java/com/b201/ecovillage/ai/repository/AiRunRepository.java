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

    /**
     * 커서가 없는 첫 페이지다. 커서 조건을 한 쿼리에 합치면 PostgreSQL이 null 파라미터의
     * 타입을 추론하지 못해 "could not determine data type of parameter" 오류가 나므로
     * 첫 페이지와 이후 페이지를 분리한다.
     */
    @Query("""
        select run
          from AiRun run
         where run.classroomId = :classroomId
         order by run.requestedAt desc, run.id desc
        """)
    List<AiRun> findFirstHistoryPage(
        @Param("classroomId") UUID classroomId,
        Pageable pageable
    );

    @Query("""
        select run
          from AiRun run
         where run.classroomId = :classroomId
           and (
               run.requestedAt < :cursorRequestedAt
               or (run.requestedAt = :cursorRequestedAt and run.id < :cursorId)
           )
         order by run.requestedAt desc, run.id desc
        """)
    List<AiRun> findHistoryAfter(
        @Param("classroomId") UUID classroomId,
        @Param("cursorRequestedAt") Instant cursorRequestedAt,
        @Param("cursorId") UUID cursorId,
        Pageable pageable
    );
}
