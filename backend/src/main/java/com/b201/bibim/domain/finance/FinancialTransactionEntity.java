package com.b201.bibim.domain.finance;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "financial_transactions")
public class FinancialTransactionEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "settlement_run_id")
    private UUID settlementRunId;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Column(nullable = false, length = 255)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected FinancialTransactionEntity() {
    }

    public static FinancialTransactionEntity success(
            UUID roomId,
            UUID sessionId,
            UUID studentId,
            UUID actorId,
            UUID settlementRunId,
            String transactionType,
            String idempotencyKey,
            String description,
            Map<String, Object> metadata
    ) {
        FinancialTransactionEntity transaction = new FinancialTransactionEntity();
        transaction.id = UUID.randomUUID();
        transaction.roomId = roomId;
        transaction.sessionId = sessionId;
        transaction.studentId = studentId;
        transaction.actorId = actorId;
        transaction.settlementRunId = settlementRunId;
        transaction.transactionType = transactionType;
        transaction.status = "SUCCESS";
        transaction.actorType = "TEACHER";
        transaction.idempotencyKey = idempotencyKey;
        transaction.description = description;
        transaction.metadata = Map.copyOf(metadata);
        transaction.createdAt = Instant.now();
        transaction.completedAt = transaction.createdAt;
        return transaction;
    }

    public UUID getId() {
        return id;
    }
}
