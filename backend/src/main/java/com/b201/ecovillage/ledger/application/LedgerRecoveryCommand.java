package com.b201.ecovillage.ledger.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.b201.ecovillage.ledger.domain.LedgerActorType;

public record LedgerRecoveryCommand(
    UUID classroomId,
    UUID originalTransactionGroupId,
    LedgerRecoveryType recoveryType,
    LedgerActorType actorType,
    UUID actorId,
    String routeKey,
    String idempotencyKey,
    String requestHash,
    String correlationId,
    String reason,
    Instant occurredAt
) {

    public LedgerRecoveryCommand {
        Objects.requireNonNull(classroomId, "classroomId must not be null");
        Objects.requireNonNull(
            originalTransactionGroupId,
            "originalTransactionGroupId must not be null"
        );
        Objects.requireNonNull(recoveryType, "recoveryType must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        routeKey = requiredText(routeKey, 160, "routeKey");
        idempotencyKey = requiredText(idempotencyKey, 128, "idempotencyKey");
        requestHash = requiredText(requestHash, 128, "requestHash");
        correlationId = requiredText(correlationId, 128, "correlationId");
        reason = requiredText(reason, 2_000, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String requiredText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
