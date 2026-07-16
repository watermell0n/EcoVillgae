package com.b201.bibim.feature.quiz;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.quiz.QuizDto.TeacherQuizSummaryResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}")
public class TeacherQuizController {

    private final QuizService quizService;

    public TeacherQuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/quiz-summary")
    ApiResponse<TeacherQuizSummaryResponse> summary(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                quizService.teacherSummary(principal.id(), roomId),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}
