package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentPayslipResponse(List<PayslipItem> payslips) {

    public record PayslipItem(
        UUID resourceId,
        String periodKey,
        String roleName,
        String status,
        int version,
        long stateVersion,
        long grossAmount,
        long deductionAmount,
        long taxAmount,
        long netAmount,
        Instant issuedAt
    ) {
    }
}
