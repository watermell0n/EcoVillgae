package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.b201.ecovillage.ai.contract.BalancingContract;

public record TeacherChatCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    String question,
    Instant periodFrom,
    Instant periodTo,
    Set<BalancingContract.MetricCode> metricCodes
) {
    public TeacherChatCommand {
        metricCodes = metricCodes == null ? Set.of() : Set.copyOf(metricCodes);
    }
}
