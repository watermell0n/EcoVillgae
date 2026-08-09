package com.b201.ecovillage.classroom.application;

import java.time.Instant;
import java.util.UUID;

public record StudentPayslipView(
    UUID resourceId,
    String periodKey,
    String roleName,
    long grossAmount,
    long deductionAmount,
    long taxAmount,
    long netAmount,
    Instant issuedAt
) {
}
