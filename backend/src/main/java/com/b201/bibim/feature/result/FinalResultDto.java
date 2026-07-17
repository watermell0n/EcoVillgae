package com.b201.bibim.feature.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FinalResultDto {

    private FinalResultDto() {
    }

    public record PurchaseResultResponse(
            UUID purchaseId,
            UUID productId,
            String productName,
            int quantity,
            long unitPrice,
            long totalPrice,
            Instant purchasedAt
    ) {
    }

    public record VoteDecisionResponse(
            UUID voteSessionId,
            UUID optionId,
            String title,
            String description,
            long requiredAmount,
            long voteCount,
            long submittedCount
    ) {
    }

    public record StudentFinalResultResponse(
            UUID roomId,
            UUID studentId,
            int participantNumber,
            String nickname,
            long grossIncome,
            long incomeTax,
            long netIncome,
            long depositPrincipal,
            long depositInterest,
            long investmentPrincipal,
            long investmentProfit,
            long financialTax,
            long day2QuizReward,
            long totalPurchaseAmount,
            long finalWalletBalance,
            List<PurchaseResultResponse> purchases,
            VoteDecisionResponse voteResult,
            String surveyUrl
    ) {
    }

    public record TeacherStudentResultResponse(
            UUID studentId,
            int participantNumber,
            String nickname,
            long grossIncome,
            long totalTax,
            long depositInterest,
            long investmentProfit,
            long quizReward,
            long purchaseAmount,
            long finalWalletBalance
    ) {
    }

    public record TeacherFinalSummaryResponse(
            UUID roomId,
            String roomName,
            int studentCount,
            long totalGrossIncome,
            long totalIncomeTax,
            long totalDepositInterest,
            long totalInvestmentProfit,
            long totalFinancialTax,
            long totalQuizReward,
            long totalPurchaseAmount,
            long totalFinalWalletBalance,
            long classFundBalance,
            long classExpenseAmount,
            VoteDecisionResponse voteResult,
            String surveyUrl,
            List<TeacherStudentResultResponse> students
    ) {
    }

    public record ProductDistributionItemResponse(
            UUID productId,
            String productName,
            UUID studentId,
            int participantNumber,
            String nickname,
            int quantity,
            long paidAmount
    ) {
    }

    public record ProductDistributionResponse(
            UUID roomId,
            int totalQuantity,
            List<ProductDistributionItemResponse> items
    ) {
    }
}

