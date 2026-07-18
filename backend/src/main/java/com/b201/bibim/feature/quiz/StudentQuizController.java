package com.b201.bibim.feature.quiz;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.quiz.QuizDto.SaveAnswerRequest;
import com.b201.bibim.feature.quiz.QuizDto.SaveAnswerResponse;
import com.b201.bibim.feature.quiz.QuizDto.StudentQuizResponse;
import com.b201.bibim.feature.quiz.QuizDto.StudentQuizResultResponse;
import com.b201.bibim.feature.quiz.QuizDto.SubmitQuizResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/quizzes")
public class StudentQuizController {

    private final QuizService quizService;

    public StudentQuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/current")
    ApiResponse<StudentQuizResponse> current(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                quizService.currentQuiz(principal.id()),
                requestId(request)
        );
    }

    @GetMapping("/day-1/result")
    ApiResponse<StudentQuizResultResponse> dayOneResult(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                quizService.dayOneResult(principal.id()),
                requestId(request)
        );
    }

    @PutMapping("/{quizId}/answers/{questionId}")
    ApiResponse<SaveAnswerResponse> saveAnswer(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID quizId,
            @PathVariable UUID questionId,
            @Valid @RequestBody SaveAnswerRequest answerRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                quizService.saveAnswer(
                        principal.id(),
                        quizId,
                        questionId,
                        answerRequest
                ),
                requestId(request)
        );
    }

    @PostMapping("/{quizId}/submit")
    ApiResponse<SubmitQuizResponse> submit(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID quizId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                quizService.submit(principal.id(), quizId),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
