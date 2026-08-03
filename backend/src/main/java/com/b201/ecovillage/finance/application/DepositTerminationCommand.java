package com.b201.ecovillage.finance.application;

import java.util.UUID;

public record DepositTerminationCommand(
    String studentSubject,
    UUID classroomId,
    UUID accountId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    String command
) {
}
