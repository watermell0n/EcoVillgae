package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record SalaryPayoutResult(
    UUID transactionGroupId,
    String status,
    long stateVersion
) {
}
