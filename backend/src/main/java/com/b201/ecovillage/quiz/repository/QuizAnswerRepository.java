package com.b201.ecovillage.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.QuizAnswer;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

    boolean existsByClassroomIdAndQuizSubmissionIdAndQuizSessionQuestionId(
        UUID classroomId,
        UUID quizSubmissionId,
        UUID quizSessionQuestionId
    );

    List<QuizAnswer> findAllByClassroomIdAndQuizSubmissionIdIn(
        UUID classroomId,
        Collection<UUID> quizSubmissionIds
    );
}
