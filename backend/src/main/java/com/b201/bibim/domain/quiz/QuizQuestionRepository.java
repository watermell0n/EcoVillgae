package com.b201.bibim.domain.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, UUID> {

    List<QuizQuestionEntity> findAllByQuizIdOrderByOrderNo(UUID quizId);

    Optional<QuizQuestionEntity> findByIdAndQuizId(UUID questionId, UUID quizId);
}
