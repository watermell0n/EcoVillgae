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

    public record QuizResultQuestionResponse(
            UUID questionId,
            int orderNo,
            String prompt,
            List<QuizOptionResponse> options,
            String selectedAnswer,
            String correctAnswer,
            boolean correct,
            String explanation
    ) {
    }

    public record StudentQuizResultResponse(
            UUID quizId,
            String title,
            int questionCount,
            int answeredCount,
            int correctCount,
            List<QuizResultQuestionResponse> questions
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
            Instant submittedAt,
            List<TeacherQuizStudentQuestionResponse> questions
    ) {
    }

    public record TeacherQuizStudentQuestionResponse(
            UUID questionId,
            int orderNo,
            boolean answered,
            boolean correct
    ) {
    }

    public record TeacherQuizQuestionSummaryResponse(
            UUID questionId,
            int orderNo,
            String prompt,
            List<QuizOptionResponse> options,
            String correctAnswer,
            String explanation,
            long answeredCount,
            long correctCount
    ) {
    }

    public record TeacherQuizSummaryResponse(
            UUID quizId,
            String title,
            String status,
            int questionCount,
            int expectedStudentCount,
            int eligibleStudentCount,
            long submittedCount,
            long inProgressCount,
            long notStartedCount,
            long notSubmittedCount,
            List<TeacherQuizStudentResponse> students,
            List<TeacherQuizQuestionSummaryResponse> questions
    ) {
    }
}
