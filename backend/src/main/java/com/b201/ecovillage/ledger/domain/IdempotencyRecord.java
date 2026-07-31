package com.b201.ecovillage.ledger.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "route_key", nullable = false, updatable = false, length = 160)
    private String routeKey;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 128)
    private String requestHash;

    @Column(name = "response_status", nullable = false, updatable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, updatable = false)
    private Map<String, Object> responseBody;

    @Column(name = "transaction_group_id", updatable = false)
    private UUID transactionGroupId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(
        UUID id,
        UUID classroomId,
        UUID actorId,
        String routeKey,
        String idempotencyKey,
        String requestHash,
        int responseStatus,
        Map<String, Object> responseBody,
        UUID transactionGroupId,
        Instant expiresAt,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.routeKey = requiredText(routeKey, 160, "routeKey");
        this.idempotencyKey = requiredText(idempotencyKey, 128, "idempotencyKey");
        this.requestHash = requiredText(requestHash, 128, "requestHash");
        if (responseStatus < 100 || responseStatus > 599) {
            throw new IllegalArgumentException("responseStatus must be a valid HTTP status");
        }
        this.responseStatus = responseStatus;
        this.responseBody = new LinkedHashMap<>(
            Objects.requireNonNull(responseBody, "responseBody must not be null")
        );
        this.transactionGroupId = transactionGroupId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public static IdempotencyRecord completed(
        UUID id,
        UUID classroomId,
        UUID actorId,
        String routeKey,
        String idempotencyKey,
        String requestHash,
        int responseStatus,
        Map<String, Object> responseBody,
        UUID transactionGroupId,
        Instant expiresAt,
        Instant createdAt
    ) {
        return new IdempotencyRecord(
            id,
            classroomId,
            actorId,
            routeKey,
            idempotencyKey,
            requestHash,
            responseStatus,
            responseBody,
            transactionGroupId,
            expiresAt,
            createdAt
        );
    }

    public boolean hasRequestHash(String candidate) {
        return requestHash.equals(candidate);
    }

    public UUID getTransactionGroupId() {
        return transactionGroupId;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public Map<String, Object> getResponseBody() {
        return Map.copyOf(responseBody);
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
