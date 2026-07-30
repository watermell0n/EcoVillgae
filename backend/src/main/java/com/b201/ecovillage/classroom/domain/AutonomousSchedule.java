package com.b201.ecovillage.classroom.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "autonomous_schedules")
public class AutonomousSchedule {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "local_start_time", nullable = false)
    private LocalTime localStartTime;

    @Column(name = "local_end_time", nullable = false)
    private LocalTime localEndTime;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone;

    @Column(nullable = false)
    private boolean enabled;

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

    protected AutonomousSchedule() {
    }

    private AutonomousSchedule(
        UUID id,
        UUID classroomId,
        int dayOfWeek,
        LocalTime localStartTime,
        LocalTime localEndTime,
        String timeZone,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.dayOfWeek = (short) dayOfWeek;
        this.localStartTime = Objects.requireNonNull(localStartTime, "localStartTime must not be null");
        this.localEndTime = Objects.requireNonNull(localEndTime, "localEndTime must not be null");
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
        this.enabled = true;
        this.status = "ACTIVE";
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static AutonomousSchedule create(
        UUID id,
        UUID classroomId,
        int dayOfWeek,
        LocalTime localStartTime,
        LocalTime localEndTime,
        String timeZone,
        Instant createdAt
    ) {
        return new AutonomousSchedule(
            id,
            classroomId,
            dayOfWeek,
            localStartTime,
            localEndTime,
            timeZone,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getLocalStartTime() {
        return localStartTime;
    }

    public LocalTime getLocalEndTime() {
        return localEndTime;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
