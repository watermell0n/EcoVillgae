package com.b201.bibim.feature.fund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class FundVoteDto {

    private FundVoteDto() {
    }

    public record FundExpenseResponse(
            UUID expenseId,
            UUID costOptionId,
            String name,
            long amount,
            Instant appliedAt
    ) {
    }

    public record FundOverviewResponse(
            UUID roomId,
            long balance,
            long totalIncomeTax,
            long totalFinancialTax,
            long totalExpenses,
            FundExpenseResponse appliedExpense
    ) {
    }

    public record CostOptionResponse(
            UUID optionId,
            String name,
            long amount,
            boolean enabled,
            boolean affordable,
            boolean applied
    ) {
    }

    public record ApplyExpenseRequest(
            @NotNull(message = "적용할 공동비용을 선택해 주세요.")
            UUID costOptionId
    ) {
    }

    public record ApplyExpenseResponse(
            FundOverviewResponse fund,
            boolean replayed
    ) {
    }

    public record VoteOptionRequest(
            @NotBlank(message = "투표 선택지 이름을 입력해 주세요.")
            @Size(max = 100, message = "투표 선택지 이름은 100자 이하여야 합니다.")
            String title,
            @NotBlank(message = "투표 선택지 설명을 입력해 주세요.")
            @Size(max = 255, message = "투표 선택지 설명은 255자 이하여야 합니다.")
            String description,
            @PositiveOrZero(message = "필요 금액은 0 이상이어야 합니다.")
            long requiredAmount
    ) {
    }

    public record CreateVoteRequest(
            @NotBlank(message = "투표 질문을 입력해 주세요.")
            @Size(max = 200, message = "투표 질문은 200자 이하여야 합니다.")
            String question,
            @NotEmpty(message = "투표 선택지를 입력해 주세요.")
            @Size(min = 2, max = 5, message = "투표 선택지는 2개 이상 5개 이하여야 합니다.")
            List<@Valid VoteOptionRequest> options
    ) {
    }

    public record CastVoteRequest(
            @NotNull(message = "투표 선택지를 골라 주세요.")
            UUID optionId
    ) {
    }

    public record DecideVoteRequest(
            @NotNull(message = "최종 선택지를 골라 주세요.")
            UUID optionId
    ) {
    }

    public record VoteOptionResponse(
            UUID optionId,
            String title,
            String description,
            long requiredAmount,
            boolean affordable,
            Long voteCount,
            boolean tiedForFirst,
            boolean decided
    ) {
    }

    public record VoteParticipationResponse(
            UUID studentId,
            int participantNumber,
            String nickname
    ) {
    }

    public record VoteSessionResponse(
            UUID voteSessionId,
            UUID roomId,
            String question,
            long fundBalanceSnapshot,
            String status,
            int eligibleStudentCount,
            long submittedCount,
            boolean hasVoted,
            UUID selectedOptionId,
            UUID decidedOptionId,
            Instant openedAt,
            Instant closedAt,
            List<VoteParticipationResponse> pendingStudents,
            List<VoteOptionResponse> options
    ) {
    }
}
