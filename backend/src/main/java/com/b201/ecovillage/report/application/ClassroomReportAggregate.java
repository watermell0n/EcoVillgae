package com.b201.ecovillage.report.application;

public record ClassroomReportAggregate(
    long walletBalanceAmount,
    long spendingAmount,
    long savingsPrincipalAmount,
    long publicFundBalance,
    long activeStudentCount,
    long quizParticipantCount,
    long quizSubmissionCount
) {
}
