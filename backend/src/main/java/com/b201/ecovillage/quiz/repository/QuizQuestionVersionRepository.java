package com.b201.ecovillage.quiz.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.QuizQuestionStatus;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;

public interface QuizQuestionVersionRepository
    extends JpaRepository<QuizQuestionVersion, UUID> {

    Optional<QuizQuestionVersion>
        findTopByClassroomIdAndQuizQuestionIdOrderByVersionDesc(
            UUID classroomId,
            UUID quizQuestionId
        );

    Optional<QuizQuestionVersion> findByClassroomIdAndId(UUID classroomId, UUID id);

    Optional<QuizQuestionVersion>
        findTopByClassroomIdAndQuizQuestionIdAndStatusOrderByVersionDesc(
            UUID classroomId,
            UUID quizQuestionId,
            QuizQuestionStatus status
        );
}
