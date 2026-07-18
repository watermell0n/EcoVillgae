package com.b201.bibim.domain.quiz;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswerEntity, UUID> {

    Optional<QuizAnswerEntity> findBySubmissionIdAndQuestionId(
            UUID submissionId,
            UUID questionId
    );

    List<QuizAnswerEntity> findAllBySubmissionId(UUID submissionId);

    List<QuizAnswerEntity> findAllBySubmissionIdIn(Collection<UUID> submissionIds);

    long countBySubmissionIdAndCorrectTrue(UUID submissionId);
}
