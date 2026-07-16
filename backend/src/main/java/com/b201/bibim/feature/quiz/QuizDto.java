package com.b201.bibim.feature.quiz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class QuizDto {

    private QuizDto() {
    }

    public record QuizOptionResponse(
            String id,
            String text
    ) {
    }

    public record QuizQuestionResponse(
            UUID questionId,
            int orderNo,
            String questionType,
            String prompt,
            List<QuizOptionResponse> options,
            String savedAnswer
    ) {
    }

    public record StudentQuizResponse(
            UUID quizId,
            String title,
            int dayNumber,
            int questionCount,
            String submissionStatus,
            int answeredCount,
            Instant submittedAt,
            List<QuizQuestionResponse> questions
    ) {
    }

    public record SaveAnswerRequest(
            @NotBlank(message = "답안을 선택해 주세요.")
            @Size(max = 50, message = "답안이 너무 깁니다.")
            String answer
    ) {
    }

    public record SaveAnswerResponse(
            UUID questionId,
            String answer,
            int answeredCount,
            Instant savedAt
    ) {
    }

    public record SubmitQuizResponse(
            UUID quizId,
            String status,
            int answeredCount,
            int questionCount,
            Instant submittedAt
    ) {
    }

    public record TeacherQuizStudentResponse(
            UUID studentId,
            int participantNumber,
            String nickname,
            boolean online,
            String submissionStatus,
            Integer correctCount,
            Instant submittedAt
    ) {
    }

    public record TeacherQuizSummaryResponse(
            UUID quizId,
            String title,
            int questionCount,
            int expectedStudentCount,
            long submittedCount,
            long inProgressCount,
            long notStartedCount,
            long notSubmittedCount,
            List<TeacherQuizStudentResponse> students
    ) {
    }
}
