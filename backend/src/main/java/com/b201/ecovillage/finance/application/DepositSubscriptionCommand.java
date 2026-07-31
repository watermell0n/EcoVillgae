package com.b201.ecovillage.finance.application;

import java.util.UUID;

public record DepositSubscriptionCommand(
    String studentSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    UUID productId,
    long principal,
    int expectedProductVersion
) {
}
