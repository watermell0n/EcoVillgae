package com.b201.bibim.feature.fund;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.fund.FundVoteDto.ApplyExpenseRequest;
import com.b201.bibim.feature.fund.FundVoteDto.AddFundRequest;
import com.b201.bibim.feature.fund.FundVoteDto.ApplyExpenseResponse;
import com.b201.bibim.feature.fund.FundVoteDto.CostOptionResponse;
import com.b201.bibim.feature.fund.FundVoteDto.CreateVoteRequest;
import com.b201.bibim.feature.fund.FundVoteDto.DecideVoteRequest;
import com.b201.bibim.feature.fund.FundVoteDto.FundOverviewResponse;
import com.b201.bibim.feature.fund.FundVoteDto.VoteSessionResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}")
public class TeacherFundVoteController {

    private final FundVoteService service;

    public TeacherFundVoteController(FundVoteService service) {
        this.service = service;
    }

    @GetMapping("/class-fund")
    ApiResponse<FundOverviewResponse> fund(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.teacherFund(principal.id(), roomId), requestId(request));
    }

    @GetMapping("/fund-cost-options")
    ApiResponse<List<CostOptionResponse>> costOptions(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.costOptions(principal.id(), roomId), requestId(request));
    }

    @PostMapping("/class-fund/additions")
    ApiResponse<FundOverviewResponse> addFund(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AddFundRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.addFund(principal.id(), roomId, idempotencyKey, body),
                requestId(request)
        );
    }

    @PostMapping("/fund-expenses")
    ApiResponse<ApplyExpenseResponse> applyExpense(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ApplyExpenseRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.applyExpense(principal.id(), roomId, idempotencyKey, body.costOptionId()),
                requestId(request)
        );
    }

    @PostMapping("/vote-sessions")
    ApiResponse<VoteSessionResponse> createVote(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateVoteRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.createVote(principal.id(), roomId, body), requestId(request));
    }

    @GetMapping("/vote-sessions/current")
    ApiResponse<VoteSessionResponse> currentVote(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.teacherVote(principal.id(), roomId), requestId(request));
    }

    @PostMapping("/vote-sessions/{voteId}/open")
    ApiResponse<VoteSessionResponse> openVote(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID voteId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.openVote(principal.id(), roomId, voteId), requestId(request));
    }

    @PostMapping("/vote-sessions/{voteId}/close")
    ApiResponse<VoteSessionResponse> closeVote(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID voteId,
            @RequestParam(defaultValue = "false") boolean force,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.closeVote(principal.id(), roomId, voteId, force),
                requestId(request)
        );
    }

    @PostMapping("/vote-sessions/{voteId}/decide")
    ApiResponse<VoteSessionResponse> decideVote(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID voteId,
            @Valid @RequestBody DecideVoteRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.decideVote(principal.id(), roomId, voteId, body), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
