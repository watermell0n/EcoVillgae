package com.b201.ecovillage.quiz.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionStatus;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;

public interface QuizQuestionVersionRepository
    extends JpaRepository<QuizQuestionVersion, UUID> {

    Optional<QuizQuestionVersion>
        findTopByClassroomIdAndQuizQuestionIdOrderByVersionDesc(
            UUID classroomId,
            UUID quizQuestionId
        );

    Optional<QuizQuestionVersion> findByClassroomIdAndId(UUID classroomId, UUID id);

    List<QuizQuestionVersion> findAllByClassroomIdAndIdIn(
        UUID classroomId,
        List<UUID> ids
    );

    Optional<QuizQuestionVersion>
        findTopByClassroomIdAndQuizQuestionIdAndStatusOrderByVersionDesc(
            UUID classroomId,
            UUID quizQuestionId,
            QuizQuestionStatus status
        );

    /**
     * 문제은행을 다시 가져올 때 이미 있는 문항을 걸러 내기 위한 본문 목록이다.
     * QuizQuestion에는 출처를 적을 칸이 없어 본문으로 대조한다.
     */
    @Query("""
        select v.prompt from QuizQuestionVersion v
        where v.classroomId = :classroomId and v.contentType = :contentType
        """)
    List<String> findPromptsByClassroomIdAndContentType(
        @Param("classroomId") UUID classroomId,
        @Param("contentType") QuizContentType contentType
    );
}
