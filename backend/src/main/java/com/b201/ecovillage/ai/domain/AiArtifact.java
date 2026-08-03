package com.b201.ecovillage.ai.domain;

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
@Table(name = "ai_artifacts")
public class AiArtifact {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "ai_run_id", nullable = false, updatable = false)
    private UUID aiRunId;

    @Column(name = "artifact_type", nullable = false, updatable = false, length = 40)
    private String artifactType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> content;

    @Column(name = "schema_version", nullable = false, updatable = false, length = 20)
    private String schemaVersion;

    @Column(name = "safety_status", nullable = false, updatable = false, length = 40)
    private String safetyStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiArtifact() {
    }

    public static AiArtifact create(
        UUID id,
        UUID classroomId,
        UUID aiRunId,
        String artifactType,
        Map<String, Object> content,
        String schemaVersion,
        String safetyStatus,
        Instant now
    ) {
        AiArtifact artifact = new AiArtifact();
        artifact.id = Objects.requireNonNull(id);
        artifact.classroomId = Objects.requireNonNull(classroomId);
        artifact.aiRunId = Objects.requireNonNull(aiRunId);
        artifact.artifactType = Objects.requireNonNull(artifactType);
        artifact.content = new LinkedHashMap<>(content);
        artifact.schemaVersion = Objects.requireNonNull(schemaVersion);
        artifact.safetyStatus = Objects.requireNonNull(safetyStatus);
        artifact.createdAt = Objects.requireNonNull(now);
        return artifact;
    }
}
