package com.b201.ecovillage.quiz.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.QuizSessionQuestion;

public interface QuizSessionQuestionRepository
    extends JpaRepository<QuizSessionQuestion, UUID> {

    List<QuizSessionQuestion> findAllByClassroomIdAndQuizSessionIdOrderBySequenceNumber(
        UUID classroomId,
        UUID quizSessionId
    );

    Optional<QuizSessionQuestion> findByClassroomIdAndQuizSessionIdAndId(
        UUID classroomId,
        UUID quizSessionId,
        UUID id
    );
}
