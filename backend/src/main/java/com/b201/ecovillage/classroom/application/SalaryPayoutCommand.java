package com.b201.ecovillage.classroom.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SalaryPayoutCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    PayoutType payoutType,
    List<UUID> studentIds,
    String periodKey,
    long amount,
    int policyVersion,
    String reason,
    Long expectedStateVersion,
    Instant occurredAt
) {
}
