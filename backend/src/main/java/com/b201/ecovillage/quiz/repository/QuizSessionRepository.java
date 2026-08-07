package com.b201.ecovillage.quiz.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.quiz.domain.QuizSession;

public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {

    Optional<QuizSession> findByClassroomIdAndId(UUID classroomId, UUID id);

    List<QuizSession> findTop50ByClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    boolean existsByClassroomIdAndStatusIn(
        UUID classroomId,
        List<com.b201.ecovillage.quiz.domain.QuizSessionStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select session
        from QuizSession session
        where session.classroomId = :classroomId and session.id = :id
        """)
    Optional<QuizSession> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("id") UUID id
    );

    @Modifying
    @Query("""
        update QuizSession session
           set session.rewardPolicyPublic = :rewardPolicy,
               session.updatedAt = :changedAt,
               session.stateVersion = session.stateVersion + 1
         where session.classroomId = :classroomId
           and session.id = :id
           and session.mode = com.b201.ecovillage.quiz.domain.QuizMode.GAME
           and session.status = com.b201.ecovillage.quiz.domain.QuizSessionStatus.OPEN
           and session.stateVersion = :expectedStateVersion
        """)
    int updateRewardPolicy(
        @Param("classroomId") UUID classroomId,
        @Param("id") UUID id,
        @Param("expectedStateVersion") long expectedStateVersion,
        @Param("rewardPolicy") Map<String, Object> rewardPolicy,
        @Param("changedAt") Instant changedAt
    );
}
