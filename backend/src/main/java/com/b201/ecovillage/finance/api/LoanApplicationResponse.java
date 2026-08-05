package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

public record LoanApplicationResponse(
    UUID applicationId,
    UUID studentId,
    String studentNickname,
    long requestedAmount,
    Long approvedAmount,
    int creditScore,
    String creditLevel,
    String status,
    Instant createdAt
) { }
