package com.b201.bibim.domain.student;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_activity_progress")
public class StudentActivityProgressEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "activity_code", nullable = false, length = 50)
    private String activityCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_type", nullable = false, length = 20)
    private String attemptType;

    @Column(name = "reward_rate_bps", nullable = false)
    private int rewardRateBps;

    @Column(name = "override_reason", length = 200)
    private String overrideReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentActivityProgressEntity() {
    }

    public static StudentActivityProgressEntity available(
            UUID roomId, UUID studentId, String activityCode, String attemptType, int rewardRateBps
    ) {
        StudentActivityProgressEntity progress = new StudentActivityProgressEntity();
        progress.id = UUID.randomUUID();
        progress.roomId = roomId;
        progress.studentId = studentId;
        progress.activityCode = activityCode;
        progress.status = "AVAILABLE";
        progress.attemptType = attemptType;
        progress.rewardRateBps = rewardRateBps;
        progress.createdAt = Instant.now();
        progress.updatedAt = progress.createdAt;
        return progress;
    }

    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void waive(String reason) {
        this.status = "WAIVED";
        this.overrideReason = reason;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void reopen(String reason) {
        this.status = "AVAILABLE";
        this.attemptType = "RECOVERY";
        this.rewardRateBps = 10_000;
        this.overrideReason = reason;
        this.completedAt = null;
        this.updatedAt = Instant.now();
    }

    public UUID getStudentId() { return studentId; }
    public String getActivityCode() { return activityCode; }
    public String getStatus() { return status; }
    public String getAttemptType() { return attemptType; }
    public int getRewardRateBps() { return rewardRateBps; }
}
