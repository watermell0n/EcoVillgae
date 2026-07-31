package com.b201.ecovillage.report.application;

import java.time.Instant;
import java.util.UUID;

public record ClassroomReportView(
    UUID classroomId,
    Instant from,
    Instant to,
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
