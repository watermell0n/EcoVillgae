package com.b201.ecovillage.quiz.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.quiz.domain.QuizSession;

public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {

    Optional<QuizSession> findByClassroomIdAndId(UUID classroomId, UUID id);

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
}
