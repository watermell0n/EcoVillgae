package com.b201.ecovillage.classroom.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "classroom_modules")
public class ClassroomModule {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "module_code", nullable = false, updatable = false, length = 40)
    private String moduleCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false)
    private Map<String, Object> config;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(nullable = false, length = 40)
    private String status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClassroomModule() {
    }

    private ClassroomModule(
        UUID id,
        UUID classroomId,
        String moduleCode,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.moduleCode = Objects.requireNonNull(moduleCode, "moduleCode must not be null");
        this.config = Map.of();
        this.status = "DISABLED";
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static ClassroomModule disabled(
        UUID id,
        UUID classroomId,
        String moduleCode,
        Instant createdAt
    ) {
        return new ClassroomModule(id, classroomId, moduleCode, createdAt);
    }

    public void activate(Map<String, Object> config, Instant changedAt) {
        if (!"DISABLED".equals(status)) {
            throw new IllegalStateException("Only a disabled module can be activated");
        }
        this.config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
        this.enabledAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.stoppedAt = null;
        this.status = "ACTIVE";
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
    }

    public void disable(Instant changedAt) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an active module can be disabled");
        }
        this.status = "DISABLED";
        this.stoppedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
    }

    public void beginStopping(Map<String, Object> config, Instant changedAt) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an active module can begin stopping");
        }
        this.config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
        this.status = "STOPPING";
        this.statusChangedAt = Objects.requireNonNull(
            changedAt,
            "changedAt must not be null"
        );
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getConfig() {
        return Map.copyOf(config);
    }

    public Instant getEnabledAt() {
        return enabledAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
