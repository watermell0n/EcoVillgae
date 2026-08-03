package com.b201.ecovillage.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.QuizQuestionIssue;

public interface QuizQuestionIssueRepository
    extends JpaRepository<QuizQuestionIssue, UUID> {

    boolean existsByClassroomIdAndQuizSessionQuestionIdAndReportedByStudentId(
        UUID classroomId,
        UUID quizSessionQuestionId,
        UUID reportedByStudentId
    );

    List<QuizQuestionIssue> findAllByClassroomIdAndQuizSessionQuestionIdInAndStatus(
        UUID classroomId,
        Collection<UUID> quizSessionQuestionIds,
        String status
    );

}
