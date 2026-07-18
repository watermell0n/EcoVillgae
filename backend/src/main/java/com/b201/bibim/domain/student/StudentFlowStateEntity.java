package com.b201.bibim.domain.student;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_flow_states")
public class StudentFlowStateEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "flow_mode", nullable = false, length = 20)
    private String flowMode;

    @Column(name = "effective_stage", nullable = false, length = 50)
    private String effectiveStage;

    @Column(name = "join_status", nullable = false, length = 30)
    private String joinStatus;

    @Column(name = "reward_rate_bps", nullable = false)
    private int rewardRateBps;

    @Column(nullable = false)
    private int version;

    @Column(name = "catch_up_started_at")
    private Instant catchUpStartedAt;

    @Column(name = "catch_up_completed_at")
    private Instant catchUpCompletedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentFlowStateEntity() {
    }

    public static StudentFlowStateEntity live(UUID roomId, UUID studentId, String roomStage) {
        StudentFlowStateEntity state = base(roomId, studentId, roomStage);
        state.flowMode = "LIVE";
        state.joinStatus = "JOINED";
        state.rewardRateBps = 10_000;
        return state;
    }

    public static StudentFlowStateEntity catchUp(UUID roomId, UUID studentId) {
        StudentFlowStateEntity state = base(roomId, studentId, "DAY1_PROFILE");
        state.flowMode = "CATCH_UP";
        state.joinStatus = "PENDING";
        state.rewardRateBps = 8_000;
        state.catchUpStartedAt = state.createdAt;
        return state;
    }

    private static StudentFlowStateEntity base(UUID roomId, UUID studentId, String stage) {
        StudentFlowStateEntity state = new StudentFlowStateEntity();
        state.id = UUID.randomUUID();
        state.roomId = roomId;
        state.studentId = studentId;
        state.effectiveStage = stage;
        state.version = 0;
        state.createdAt = Instant.now();
        state.updatedAt = state.createdAt;
        return state;
    }

    public void moveTo(String stage) {
        this.flowMode = "CATCH_UP";
        this.effectiveStage = stage;
        this.rewardRateBps = 8_000;
        touch();
    }

    public void moveToRecovery(String stage) {
        this.flowMode = "CATCH_UP";
        this.effectiveStage = stage;
        this.rewardRateBps = 10_000;
        touch();
    }

    public void awaitJoin() {
        this.flowMode = "AWAITING_JOIN";
        this.joinStatus = "PENDING";
        this.catchUpCompletedAt = Instant.now();
        touch();
    }

    public void approve(UUID teacherId, String roomStage, boolean pendingBoundary) {
        this.approvedBy = teacherId;
        this.approvedAt = Instant.now();
        this.joinStatus = pendingBoundary ? "APPROVED_PENDING_BOUNDARY" : "JOINED";
        if (!pendingBoundary) {
            this.flowMode = "LIVE";
            this.effectiveStage = roomStage;
            this.rewardRateBps = 10_000;
        }
        touch();
    }

    public void joinAt(String roomStage) {
        this.flowMode = "LIVE";
        this.joinStatus = "JOINED";
        this.effectiveStage = roomStage;
        this.rewardRateBps = 10_000;
        touch();
    }

    public void syncLive(String roomStage) {
        if (!"LIVE".equals(flowMode) || roomStage.equals(effectiveStage)) return;
        this.effectiveStage = roomStage;
        touch();
    }

    public void lock(String roomStage) {
        this.flowMode = "LOCKED";
        this.joinStatus = "LOCKED";
        this.effectiveStage = roomStage;
        touch();
    }

    private void touch() {
        this.version += 1;
        this.updatedAt = Instant.now();
    }

    public UUID getRoomId() { return roomId; }
    public UUID getStudentId() { return studentId; }
    public String getFlowMode() { return flowMode; }
    public String getEffectiveStage() { return effectiveStage; }
    public String getJoinStatus() { return joinStatus; }
    public int getRewardRateBps() { return rewardRateBps; }
    public Instant getCatchUpStartedAt() { return catchUpStartedAt; }
    public Instant getCatchUpCompletedAt() { return catchUpCompletedAt; }
}
