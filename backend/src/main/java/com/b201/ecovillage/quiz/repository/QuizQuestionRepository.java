package com.b201.ecovillage.quiz.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.quiz.domain.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    Optional<QuizQuestion> findByClassroomIdAndId(UUID classroomId, UUID id);

    List<QuizQuestion> findAllByClassroomIdOrderByUpdatedAtDesc(UUID classroomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select q
        from QuizQuestion q
        where q.classroomId = :classroomId and q.id = :id
        """)
    Optional<QuizQuestion> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("id") UUID id
    );
}
