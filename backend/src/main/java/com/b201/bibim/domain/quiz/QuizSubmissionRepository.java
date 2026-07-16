package com.b201.bibim.domain.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmissionEntity, UUID> {

    Optional<QuizSubmissionEntity> findByQuizIdAndStudentId(UUID quizId, UUID studentId);

    List<QuizSubmissionEntity> findAllByQuizId(UUID quizId);
}
