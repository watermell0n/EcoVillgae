package com.b201.ecovillage.ledger.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.b201.ecovillage.ledger.domain.LedgerActorType;

public record LedgerTransactionCommand(
    UUID classroomId,
    String transactionType,
    LedgerActorType actorType,
    UUID actorId,
    UUID policySnapshotId,
    UUID recoveryOfGroupId,
    UUID rootTransactionGroupId,
    String routeKey,
    String idempotencyKey,
    String requestHash,
    String correlationId,
    String reason,
    List<LedgerTransactionPosting> postings,
    Instant occurredAt
) {

    public LedgerTransactionCommand {
        Objects.requireNonNull(classroomId, "classroomId must not be null");
        transactionType = requiredText(transactionType, 40, "transactionType");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        if ((recoveryOfGroupId == null) != (rootTransactionGroupId == null)) {
            throw new IllegalArgumentException(
                "recoveryOfGroupId and rootTransactionGroupId must be supplied together"
            );
        }
        routeKey = requiredText(routeKey, 160, "routeKey");
        idempotencyKey = requiredText(idempotencyKey, 128, "idempotencyKey");
        requestHash = requiredText(requestHash, 128, "requestHash");
        correlationId = requiredText(correlationId, 128, "correlationId");
        reason = reason == null ? null : reason.trim();
        postings = List.copyOf(Objects.requireNonNull(postings, "postings must not be null"));
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static LedgerTransactionCommand original(
        UUID classroomId,
        String transactionType,
        LedgerActorType actorType,
        UUID actorId,
        UUID policySnapshotId,
        String routeKey,
        String idempotencyKey,
        String requestHash,
        String correlationId,
        String reason,
        List<LedgerTransactionPosting> postings,
        Instant occurredAt
    ) {
        return new LedgerTransactionCommand(
            classroomId,
            transactionType,
            actorType,
            actorId,
            policySnapshotId,
            null,
            null,
            routeKey,
            idempotencyKey,
            requestHash,
            correlationId,
            reason,
            postings,
            occurredAt
        );
    }

    public static LedgerTransactionCommand recovery(
        UUID classroomId,
        String transactionType,
        LedgerActorType actorType,
        UUID actorId,
        UUID policySnapshotId,
        UUID recoveryOfGroupId,
        UUID rootTransactionGroupId,
        String routeKey,
        String idempotencyKey,
        String requestHash,
        String correlationId,
        String reason,
        List<LedgerTransactionPosting> postings,
        Instant occurredAt
    ) {
        return new LedgerTransactionCommand(
            classroomId,
            transactionType,
            actorType,
            actorId,
            policySnapshotId,
            Objects.requireNonNull(recoveryOfGroupId, "recoveryOfGroupId must not be null"),
            Objects.requireNonNull(
                rootTransactionGroupId,
                "rootTransactionGroupId must not be null"
            ),
            routeKey,
            idempotencyKey,
            requestHash,
            correlationId,
            reason,
            postings,
            occurredAt
        );
    }

    public boolean isRecovery() {
        return recoveryOfGroupId != null;
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
