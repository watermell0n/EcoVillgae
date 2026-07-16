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

    @Column(nullable = false)
    private int version;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomEntity() {
    }

    public static RoomEntity preparing(UUID teacherId, String name, int expectedStudentCount) {
        RoomEntity room = new RoomEntity();
        room.id = UUID.randomUUID();
        room.teacherId = teacherId;
        room.name = name;
        room.expectedStudentCount = expectedStudentCount;
        room.status = "PREPARING";
        room.currentDay = 1;
        room.currentStage = RoomStage.DAY1_LOBBY.name();
        room.mode = "GUIDED";
        room.version = 0;
        room.syncVersion = 0;
        room.updatedAt = Instant.now();
        return room;
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

    public int getVersion() {
        return version;
    }

    public long getSyncVersion() {
        return syncVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void changeStage(RoomStage targetStage) {
        this.currentDay = (short) targetStage.day();
        this.currentStage = targetStage.name();
        this.status = targetStage.roomStatus();
        this.mode = targetStage.mode();
        this.version += 1;
        this.syncVersion += 1;
        this.updatedAt = Instant.now();
    }

    public void markEconomicStateChanged() {
        this.syncVersion += 1;
        this.updatedAt = Instant.now();
    }

    public void markControlStateChanged() {
        this.version += 1;
        this.syncVersion += 1;
        this.updatedAt = Instant.now();
    }
}
