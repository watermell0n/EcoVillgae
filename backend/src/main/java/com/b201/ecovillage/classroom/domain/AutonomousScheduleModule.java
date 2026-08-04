package com.b201.ecovillage.classroom.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "autonomous_schedule_modules")
public class AutonomousScheduleModule {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "autonomous_schedule_id", nullable = false, updatable = false)
    private UUID autonomousScheduleId;

    @Column(name = "module_code", nullable = false, updatable = false, length = 40)
    private String moduleCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AutonomousScheduleModule() {
    }

    private AutonomousScheduleModule(
        UUID id,
        UUID classroomId,
        UUID autonomousScheduleId,
        String moduleCode,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.autonomousScheduleId = Objects.requireNonNull(
            autonomousScheduleId,
            "autonomousScheduleId must not be null"
        );
        this.moduleCode = ModuleCatalog.require(moduleCode).code();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static AutonomousScheduleModule connect(
        UUID id,
        UUID classroomId,
        UUID autonomousScheduleId,
        String moduleCode,
        Instant createdAt
    ) {
        return new AutonomousScheduleModule(
            id,
            classroomId,
            autonomousScheduleId,
            moduleCode,
            createdAt
        );
    }

    public UUID getAutonomousScheduleId() {
        return autonomousScheduleId;
    }

    public String getModuleCode() {
        return moduleCode;
    }
}
