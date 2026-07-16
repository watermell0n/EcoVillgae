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

    @Column(name = "inflation_applied_at")
    private Instant inflationAppliedAt;

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

    public Instant getInflationAppliedAt() {
        return inflationAppliedAt;
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

    public void closeDayOne() {
        this.currentDay = 1;
        this.currentStage = RoomStage.DAY1_CLOSED.name();
        this.status = "DAY1_CLOSED";
        this.mode = "CLOSED";
        this.version += 1;
        this.syncVersion += 1;
        this.updatedAt = Instant.now();
    }

    public void startDayTwo() {
        if (!RoomStage.DAY1_CLOSED.name().equals(currentStage)) {
            throw new IllegalStateException("1일차 마감 상태에서만 2일차를 시작할 수 있습니다.");
        }
        this.currentDay = 2;
        this.currentStage = RoomStage.DAY2_LOGIN.name();
        this.status = RoomStage.DAY2_LOGIN.roomStatus();
        this.mode = RoomStage.DAY2_LOGIN.mode();
        this.version += 1;
        this.syncVersion += 1;
        this.updatedAt = Instant.now();
    }

    public void markInflationApplied() {
        if (inflationAppliedAt != null) {
            throw new IllegalStateException("물가상승은 방마다 한 번만 적용할 수 있습니다.");
        }
        inflationAppliedAt = Instant.now();
        syncVersion += 1;
        updatedAt = inflationAppliedAt;
    }
}
