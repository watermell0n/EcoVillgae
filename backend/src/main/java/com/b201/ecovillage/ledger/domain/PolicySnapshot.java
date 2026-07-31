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
@Table(name = "policy_snapshots")
public class PolicySnapshot {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "snapshot_hash", nullable = false, updatable = false, length = 128)
    private String snapshotHash;

    @Column(name = "schema_version", nullable = false, updatable = false, length = 20)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculated_values", nullable = false, updatable = false)
    private Map<String, Object> calculatedValues;

    @Column(name = "rounding_rule", nullable = false, updatable = false, length = 40)
    private String roundingRule;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PolicySnapshot() {
    }

    private PolicySnapshot(
        UUID id,
        UUID classroomId,
        String snapshotHash,
        String schemaVersion,
        Map<String, Object> calculatedValues,
        String roundingRule,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.snapshotHash = requiredText(snapshotHash, 128, "snapshotHash");
        this.schemaVersion = requiredText(schemaVersion, 20, "schemaVersion");
        this.calculatedValues = new LinkedHashMap<>(
            Objects.requireNonNull(calculatedValues, "calculatedValues must not be null")
        );
        this.roundingRule = requiredText(roundingRule, 40, "roundingRule");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static PolicySnapshot capture(
        UUID id,
        UUID classroomId,
        String snapshotHash,
        String schemaVersion,
        Map<String, Object> calculatedValues,
        String roundingRule,
        Instant createdAt
    ) {
        return new PolicySnapshot(
            id,
            classroomId,
            snapshotHash,
            schemaVersion,
            calculatedValues,
            roundingRule,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public Map<String, Object> getCalculatedValues() {
        return Map.copyOf(calculatedValues);
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
