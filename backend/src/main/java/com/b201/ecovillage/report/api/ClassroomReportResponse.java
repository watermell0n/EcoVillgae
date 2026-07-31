package com.b201.ecovillage.report.api;

import java.time.Instant;
import java.util.UUID;

public record ClassroomReportResponse(
    UUID classroomId,
    ReportPeriodResponse period,
    long totalCurrencyAmount,
    long walletBalanceAmount,
    long averageWalletBalance,
    long spendingAmount,
    long savingsPrincipalAmount,
    long publicFundBalance,
    long activeStudentCount,
    long quizParticipantCount,
    long quizSubmissionCount,
    Instant generatedAt
) {
}
