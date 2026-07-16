package com.b201.bibim.feature.settlement;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.settlement.SettlementDto.Day1PayslipResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/payslips")
public class StudentPayslipController {

    private final SettlementService settlementService;

    public StudentPayslipController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/day-1")
    ApiResponse<Day1PayslipResponse> day1Payslip(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                settlementService.day1Payslip(principal.id()),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}
