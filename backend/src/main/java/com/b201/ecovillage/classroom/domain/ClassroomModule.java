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
        Instant enabledAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.moduleCode = Objects.requireNonNull(moduleCode, "moduleCode must not be null");
        this.config = Map.of();
        this.enabledAt = Objects.requireNonNull(enabledAt, "enabledAt must not be null");
        this.status = "ACTIVE";
        this.statusChangedAt = enabledAt;
        this.createdAt = enabledAt;
        this.updatedAt = enabledAt;
    }

    public static ClassroomModule activate(
        UUID id,
        UUID classroomId,
        String moduleCode,
        Instant enabledAt
    ) {
        return new ClassroomModule(id, classroomId, moduleCode, enabledAt);
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
}
