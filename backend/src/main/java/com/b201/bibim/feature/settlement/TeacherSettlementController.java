package com.b201.bibim.feature.settlement;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.settlement.SettlementDto.Day1IncomePreviewResponse;
import com.b201.bibim.feature.settlement.SettlementDto.Day1IncomeSettlementResponse;
import com.b201.bibim.feature.settlement.Day2FinanceDto.SettlementResponse;
import com.b201.bibim.feature.settlement.Day2FinanceDto.TeacherFinancePreviewResponse;
import com.b201.bibim.feature.settlement.Day2QuizRewardDto.TeacherRewardPreviewResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}/settlements")
public class TeacherSettlementController {

    private final SettlementService settlementService;
    private final Day2FinanceService day2FinanceService;
    private final Day2QuizRewardService day2QuizRewardService;

    public TeacherSettlementController(
            SettlementService settlementService,
            Day2FinanceService day2FinanceService,
            Day2QuizRewardService day2QuizRewardService
    ) {
        this.settlementService = settlementService;
        this.day2FinanceService = day2FinanceService;
        this.day2QuizRewardService = day2QuizRewardService;
    }

    @GetMapping("/day-1-income/preview")
    ApiResponse<Day1IncomePreviewResponse> previewDay1Income(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                settlementService.previewDay1Income(principal.id(), roomId),
                requestId(request)
        );
    }

    @PostMapping("/day-1-income")
    ApiResponse<Day1IncomeSettlementResponse> settleDay1Income(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                settlementService.settleDay1Income(
                        principal.id(),
                        roomId,
                        idempotencyKey
                ),
                requestId(request)
        );
    }

    @GetMapping("/day-2-finance/preview")
    ApiResponse<TeacherFinancePreviewResponse> previewDay2Finance(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                day2FinanceService.preview(principal.id(), roomId),
                requestId(request)
        );
    }

    @PostMapping("/day-2-finance")
    ApiResponse<SettlementResponse> settleDay2Finance(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                day2FinanceService.settle(principal.id(), roomId, idempotencyKey),
                requestId(request)
        );
    }

    @GetMapping("/day-2-quiz-reward/preview")
    ApiResponse<TeacherRewardPreviewResponse> previewDay2QuizReward(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                day2QuizRewardService.preview(principal.id(), roomId),
                requestId(request)
        );
    }

    @PostMapping("/day-2-quiz-reward")
    ApiResponse<Day2QuizRewardDto.SettlementResponse> settleDay2QuizReward(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                day2QuizRewardService.settle(principal.id(), roomId, idempotencyKey),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
