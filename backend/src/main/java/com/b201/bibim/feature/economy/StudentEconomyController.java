package com.b201.bibim.feature.economy;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.economy.EconomyDto.OverviewResponse;
import com.b201.bibim.feature.economy.EconomyDto.TransactionHistoryResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/economy")
public class StudentEconomyController {

    private final EconomyService economyService;

    public StudentEconomyController(EconomyService economyService) {
        this.economyService = economyService;
    }

    @GetMapping("/overview")
    ApiResponse<OverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(economyService.overview(principal.id()), requestId(request));
    }

    @GetMapping("/transactions")
    ApiResponse<TransactionHistoryResponse> transactions(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(economyService.transactions(principal.id()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
