package com.b201.ecovillage.finance.application;

import java.util.UUID;

public record SavingsSubscriptionCommand(
    String studentSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    UUID productId,
    long installmentAmount,
    int expectedProductVersion
) { }
