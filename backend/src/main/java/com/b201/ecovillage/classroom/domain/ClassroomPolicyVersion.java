package com.b201.ecovillage.classroom.domain;

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
@Table(name = "classroom_policy_versions")
public class ClassroomPolicyVersion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "policy_area", nullable = false, updatable = false, length = 40)
    private String policyArea;

    @Column(nullable = false, updatable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"values\"", nullable = false, updatable = false)
    private Map<String, Object> policyValues;

    @Column(name = "schema_version", nullable = false, updatable = false, length = 20)
    private String schemaVersion;

    @Column(nullable = false, updatable = false, length = 40)
    private String source;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClassroomPolicyVersion() {
    }

    private ClassroomPolicyVersion(
        UUID id,
        UUID classroomId,
        String policyArea,
        int version,
        Map<String, Object> policyValues,
        String source,
        Instant effectiveFrom
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.policyArea = Objects.requireNonNull(policyArea, "policyArea must not be null");
        this.version = version;
        this.policyValues = new LinkedHashMap<>(
            Objects.requireNonNull(policyValues, "policyValues must not be null")
        );
        this.schemaVersion = "1";
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.status = "ACTIVE";
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.createdAt = effectiveFrom;
    }

    public static ClassroomPolicyVersion salary(
        UUID id,
        UUID classroomId,
        int version,
        Map<String, Object> policyValues,
        String source,
        Instant effectiveFrom
    ) {
        return new ClassroomPolicyVersion(
            id,
            classroomId,
            "SALARY",
            version,
            policyValues,
            source,
            effectiveFrom
        );
    }

    public static ClassroomPolicyVersion accessMode(
        UUID id,
        UUID classroomId,
        int version,
        Map<String, Object> policyValues,
        Instant effectiveFrom
    ) {
        return new ClassroomPolicyVersion(
            id,
            classroomId,
            "ACCESS_MODE",
            version,
            policyValues,
            "TEACHER",
            effectiveFrom
        );
    }

    public static ClassroomPolicyVersion policy(
        UUID id,
        UUID classroomId,
        String policyArea,
        int version,
        Map<String, Object> policyValues,
        String source,
        Instant effectiveFrom
    ) {
        return new ClassroomPolicyVersion(
            id,
            classroomId,
            policyArea,
            version,
            policyValues,
            source,
            effectiveFrom
        );
    }

    public void supersede(Instant changedAt) {
        this.status = "SUPERSEDED";
        this.supersededAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getPolicyArea() {
        return policyArea;
    }

    public int getVersion() {
        return version;
    }

    public Map<String, Object> getPolicyValues() {
        return Map.copyOf(policyValues);
    }

    public String getStatus() {
        return status;
    }
}
