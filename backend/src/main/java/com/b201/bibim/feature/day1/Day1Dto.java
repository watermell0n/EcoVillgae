package com.b201.bibim.feature.day1;

import java.util.List;
import java.util.UUID;

public final class Day1Dto {

    private Day1Dto() {
    }

    public record PurchaseSummaryResponse(
            UUID purchaseId,
            String productName,
            int quantity,
            long totalPrice
    ) {
    }

    public record InvestmentAllocationSummaryResponse(
            UUID assetId,
            String assetName,
            String riskLevel,
            long amount
    ) {
    }

    public record StudentDay1SummaryResponse(
            UUID studentId,
            int participantNumber,
            String nickname,
            String roomStage,
            long walletBalance,
            long depositPrincipal,
            long expectedDepositInterest,
            long investmentPrincipal,
            long purchaseTotal,
            long totalAssets,
            List<InvestmentAllocationSummaryResponse> investments,
            List<PurchaseSummaryResponse> purchases
    ) {
    }

    public record TeacherStudentDay1Response(
            UUID studentId,
            int participantNumber,
            String nickname,
            long walletBalance,
            long depositPrincipal,
            long investmentPrincipal,
            long purchaseTotal,
            boolean depositConfirmed,
            boolean investmentConfirmed,
            boolean purchased,
            boolean inactive
    ) {
    }

    public record TeacherDay1SummaryResponse(
            UUID roomId,
            String roomStage,
            int studentCount,
            long totalWalletBalance,
            long totalDepositPrincipal,
            long totalInvestmentPrincipal,
            long totalPurchaseAmount,
            long depositConfirmedCount,
            long investmentConfirmedCount,
            long purchaseParticipantCount,
            long inactiveStudentCount,
            List<TeacherStudentDay1Response> students
    ) {
    }

    public record ClosePreviewResponse(
            UUID roomId,
            boolean canClose,
            boolean incomeSettlementCompleted,
            long depositDraftCount,
            long investmentDraftCount,
            boolean pendingTransactionExists,
            List<String> warnings
    ) {
    }
}
