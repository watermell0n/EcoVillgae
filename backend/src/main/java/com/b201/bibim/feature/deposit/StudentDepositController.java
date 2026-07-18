package com.b201.bibim.feature.deposit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.deposit.DepositDto.DepositResponse;
import com.b201.bibim.feature.deposit.DepositDto.DepositHistoryResponse;
import com.b201.bibim.feature.deposit.DepositDto.SaveDraftRequest;
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
@RequestMapping("/api/v1/student/deposits")
public class StudentDepositController {

    private final DepositService depositService;

    public StudentDepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @GetMapping("/current")
    ApiResponse<DepositResponse> current(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(depositService.current(principal.id()), requestId(request));
    }

    @GetMapping("/history")
    ApiResponse<DepositHistoryResponse> history(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(depositService.history(principal.id()), requestId(request));
    }

    @PutMapping("/draft")
    ApiResponse<DepositResponse> saveDraft(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody SaveDraftRequest saveDraftRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                depositService.saveDraft(principal.id(), saveDraftRequest),
                requestId(request)
        );
    }

    @DeleteMapping("/draft")
    ApiResponse<DepositResponse> cancelDraft(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(depositService.cancelDraft(principal.id()), requestId(request));
    }

    @PostMapping("/confirm")
    ApiResponse<DepositResponse> confirm(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                depositService.confirm(principal.id(), idempotencyKey),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
