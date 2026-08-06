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

    /**
     * 이 학급에서 퀴즈로 한 번이라도 열린 문항 버전입니다. 교사 화면이 이미 진행한 문항을
     * 새로고침 뒤에도 구분하려면 세션 하나가 아니라 학급 전체 기록이 필요합니다.
     */
    @org.springframework.data.jpa.repository.Query(
        "select distinct question.quizQuestionVersionId from QuizSessionQuestion question "
            + "where question.classroomId = :classroomId"
    )
    List<UUID> findUsedQuestionVersionIds(
        @org.springframework.data.repository.query.Param("classroomId") UUID classroomId
    );
}
