package com.b201.bibim.feature.investment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.investment.InvestmentDto.InvestmentResponse;
import com.b201.bibim.feature.investment.InvestmentDto.SaveDraftRequest;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/investments")
public class StudentInvestmentController {

    private final InvestmentService investmentService;

    public StudentInvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @GetMapping("/current")
    ApiResponse<InvestmentResponse> current(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(investmentService.current(principal.id()), requestId(request));
    }

    @PutMapping("/draft")
    ApiResponse<InvestmentResponse> saveDraft(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody SaveDraftRequest saveDraftRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                investmentService.saveDraft(principal.id(), saveDraftRequest),
                requestId(request)
        );
    }

    @DeleteMapping("/draft")
    ApiResponse<InvestmentResponse> cancelDraft(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(investmentService.cancelDraft(principal.id()), requestId(request));
    }

    @PostMapping("/confirm")
    ApiResponse<InvestmentResponse> confirm(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                investmentService.confirm(principal.id(), idempotencyKey),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
