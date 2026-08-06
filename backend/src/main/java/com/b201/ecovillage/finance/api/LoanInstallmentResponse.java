package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

public record LoanInstallmentResponse(
    UUID installmentId,
    int installmentNumber,
    long scheduledPrincipal,
    long scheduledInterest,
    Instant dueAt,
    Instant graceUntil,
    long paidPrincipal,
    long paidInterest,
    Instant paidAt,
    String status,
    long stateVersion
) { }
