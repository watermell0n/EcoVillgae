package com.b201.ecovillage.quiz.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.QuizStudentResult;

public interface QuizStudentResultRepository
    extends JpaRepository<QuizStudentResult, UUID> {

    List<QuizStudentResult> findAllByQuizResultVersionIdOrderByRankAsc(
        UUID quizResultVersionId
    );

    Optional<QuizStudentResult> findByQuizResultVersionIdAndClassroomStudentId(
        UUID quizResultVersionId,
        UUID classroomStudentId
    );
}
