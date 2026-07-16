package com.b201.bibim.feature.settlement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SettlementDto {

    private SettlementDto() {
    }

    public record StudentIncomeResponse(
            UUID studentId,
            int participantNumber,
            String nickname,
            boolean quizSubmitted,
            int correctCount,
            long roleIncome,
            long participationReward,
            long correctReward,
            long grossIncome,
            long incomeTax,
            long netIncome
    ) {
    }

    public record Day1IncomePreviewResponse(
            UUID roomId,
            String settlementStatus,
            int incomeTaxBps,
            int targetCount,
            long totalGrossIncome,
            long totalIncomeTax,
            long totalNetIncome,
            long classFundBalance,
            Instant completedAt,
            List<StudentIncomeResponse> students
    ) {
    }

    public record Day1IncomeSettlementResponse(
            Day1IncomePreviewResponse settlement,
            boolean replayed
    ) {
    }

    public record Day1PayslipResponse(
            UUID settlementRunId,
            UUID studentId,
            int participantNumber,
            String nickname,
            boolean quizSubmitted,
            int correctCount,
            long roleIncome,
            long participationReward,
            long correctReward,
            long grossIncome,
            long incomeTax,
            long netIncome,
            long walletBalance,
            Instant issuedAt
    ) {
    }
}
