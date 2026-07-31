package com.b201.ecovillage.quiz.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizSubmission;

public interface QuizSubmissionRepository
    extends JpaRepository<QuizSubmission, UUID> {

    Optional<QuizSubmission>
        findTopByClassroomIdAndQuizSessionIdAndClassroomStudentIdAndModeOrderByAnswerSheetVersionDesc(
            UUID classroomId,
            UUID quizSessionId,
            UUID classroomStudentId,
            QuizMode mode
        );
}
