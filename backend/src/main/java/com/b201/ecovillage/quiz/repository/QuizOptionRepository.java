package com.b201.ecovillage.quiz.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.QuizOption;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

    List<QuizOption> findAllByClassroomIdAndQuizQuestionVersionIdOrderBySortOrder(
        UUID classroomId,
        UUID quizQuestionVersionId
    );
}
