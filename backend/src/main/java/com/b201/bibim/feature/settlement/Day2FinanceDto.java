package com.b201.bibim.feature.settlement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Day2FinanceDto {

    private Day2FinanceDto() {
    }

    public record AllocationResultResponse(
            UUID assetId,
            String assetName,
            String riskLevel,
            long principal,
            int returnBps,
            long profit,
            long finalValue
    ) {
    }

    public record StudentFinanceResponse(
            UUID studentId,
            int participantNumber,
            String nickname,
            String settlementStatus,
            boolean resultAvailable,
            long walletBefore,
            long depositPrincipal,
            long depositInterest,
            long investmentPrincipal,
            long investmentProfit,
            long investmentValue,
            long taxableProfit,
            long financialTax,
            long walletAfter,
            List<AllocationResultResponse> allocations,
            Instant completedAt
    ) {
    }

    public record TeacherFinancePreviewResponse(
            UUID roomId,
            String settlementStatus,
            int financialTaxBps,
            int targetCount,
            long totalDepositPrincipal,
            long totalDepositInterest,
            long totalInvestmentPrincipal,
            long totalInvestmentProfit,
            long totalFinancialTax,
            long totalWalletAfter,
            long classFundBalance,
            Instant completedAt,
            List<StudentFinanceResponse> students
    ) {
    }

    public record SettlementResponse(
            TeacherFinancePreviewResponse settlement,
            boolean replayed
    ) {
    }
}
