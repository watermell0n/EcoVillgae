package com.b201.bibim.domain.room;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms")
public class RoomEntity {

    @Id
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "expected_student_count", nullable = false)
    private int expectedStudentCount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "current_day", nullable = false)
    private short currentDay;

    @Column(name = "current_stage", nullable = false, length = 50)
    private String currentStage;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getName() {
        return name;
    }

    public int getExpectedStudentCount() {
        return expectedStudentCount;
    }

    public String getStatus() {
        return status;
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public String getMode() {
        return mode;
    }

    public long getSyncVersion() {
        return syncVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
