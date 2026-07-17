package com.b201.bibim.feature.fund;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.fund.FundVoteDto.CastVoteRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student")
public class StudentFundVoteController {

    private final FundVoteService service;

    public StudentFundVoteController(FundVoteService service) {
        this.service = service;
    }

    @GetMapping("/class-fund")
    ApiResponse<FundOverviewResponse> fund(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.studentFund(principal.id()), requestId(request));
    }

    @GetMapping("/votes/current")
    ApiResponse<VoteSessionResponse> currentVote(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.studentVote(principal.id()), requestId(request));
    }

    @PostMapping("/votes/{voteSessionId}")
    ApiResponse<VoteSessionResponse> castVote(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID voteSessionId,
            @Valid @RequestBody CastVoteRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.castVote(principal.id(), voteSessionId, body), requestId(request));
    }

    @GetMapping("/votes/{voteSessionId}/result")
    ApiResponse<VoteSessionResponse> result(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID voteSessionId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.voteResult(principal.id(), voteSessionId), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}

