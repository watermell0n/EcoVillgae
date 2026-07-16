package com.b201.bibim.domain.settlement;

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
@Table(name = "settlement_runs")
public class SettlementRunEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "started_by", nullable = false)
    private UUID startedBy;

    @Column(name = "settlement_type", nullable = false, length = 40)
    private String settlementType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SettlementRunEntity() {
    }

    public static SettlementRunEntity processing(
            UUID roomId,
            UUID sessionId,
            UUID startedBy,
            String idempotencyKey,
            int targetCount
    ) {
        SettlementRunEntity run = new SettlementRunEntity();
        run.id = UUID.randomUUID();
        run.roomId = roomId;
        run.sessionId = sessionId;
        run.startedBy = startedBy;
        run.settlementType = "DAY1_INCOME";
        run.status = "PROCESSING";
        run.idempotencyKey = idempotencyKey;
        run.targetCount = targetCount;
        run.successCount = 0;
        run.failureCount = 0;
        run.summary = Map.of();
        run.startedAt = Instant.now();
        return run;
    }

    public void complete(long totalGrossIncome, long totalIncomeTax, long totalNetIncome) {
        status = "COMPLETED";
        successCount = targetCount;
        failureCount = 0;
        summary = Map.of(
                "totalGrossIncome", totalGrossIncome,
                "totalIncomeTax", totalIncomeTax,
                "totalNetIncome", totalNetIncome
        );
        completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
