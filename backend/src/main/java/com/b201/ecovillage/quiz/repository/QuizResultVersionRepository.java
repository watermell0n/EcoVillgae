package com.b201.ecovillage.quiz.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.quiz.domain.QuizResultVersion;

public interface QuizResultVersionRepository
    extends JpaRepository<QuizResultVersion, UUID> {

    Optional<QuizResultVersion> findTopByClassroomIdAndQuizSessionIdOrderByVersionDesc(
        UUID classroomId,
        UUID quizSessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select result
          from QuizResultVersion result
         where result.classroomId = :classroomId
           and result.quizSessionId = :quizSessionId
           and result.version = (
               select max(candidate.version)
                 from QuizResultVersion candidate
                where candidate.classroomId = :classroomId
                  and candidate.quizSessionId = :quizSessionId
           )
        """)
    Optional<QuizResultVersion> findLatestForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("quizSessionId") UUID quizSessionId
    );
}
