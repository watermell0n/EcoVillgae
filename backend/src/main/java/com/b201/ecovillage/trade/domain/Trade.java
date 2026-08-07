package com.b201.ecovillage.trade.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "proposer_student_id", nullable = false, updatable = false)
    private UUID proposerStudentId;

    @Column(name = "counterpart_student_id", nullable = false, updatable = false)
    private UUID counterpartStudentId;

    @Column(columnDefinition = "text", updatable = false)
    private String description;

    @Column(name = "policy_snapshot_id")
    private UUID policySnapshotId;

    @Column(name = "transaction_group_id")
    private UUID transactionGroupId;

    @Column(name = "counterpart_decided_at")
    private Instant counterpartDecidedAt;

    @Column(name = "teacher_decided_at")
    private Instant teacherDecidedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TradeStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trade() {
    }

    private Trade(
        UUID id,
        UUID classroomId,
        UUID proposerStudentId,
        UUID counterpartStudentId,
        String description,
        Instant expiresAt,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.proposerStudentId = Objects.requireNonNull(
            proposerStudentId,
            "proposerStudentId must not be null"
        );
        this.counterpartStudentId = Objects.requireNonNull(
            counterpartStudentId,
            "counterpartStudentId must not be null"
        );
        if (proposerStudentId.equals(counterpartStudentId)) {
            throw new TradeOperationException(
                "SELF_TRADE_FORBIDDEN",
                "본인과는 개인거래를 만들 수 없습니다."
            );
        }
        this.description = normalizedDescription(description);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        this.status = TradeStatus.AWAITING_COUNTERPART;
        this.statusChangedAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Trade propose(
        UUID id,
        UUID classroomId,
        UUID proposerStudentId,
        UUID counterpartStudentId,
        String description,
        Instant expiresAt,
        Instant createdAt
    ) {
        return new Trade(
            id,
            classroomId,
            proposerStudentId,
            counterpartStudentId,
            description,
            expiresAt,
            createdAt
        );
    }

    public void cancelByProposer(UUID actorStudentId, Instant changedAt) {
        if (!proposerStudentId.equals(actorStudentId)) {
            throw new TradeOperationException(
                "RESOURCE_NOT_OWNED",
                "제안자만 거래를 취소할 수 있습니다."
            );
        }
        if (
            status != TradeStatus.AWAITING_COUNTERPART
                && status != TradeStatus.AWAITING_TEACHER
        ) {
            invalidTransition();
        }
        changeStatus(TradeStatus.CANCELED, changedAt);
    }

    public void acceptByCounterpart(UUID actorStudentId, Instant changedAt) {
        requireCounterpart(actorStudentId);
        requireStatus(TradeStatus.AWAITING_COUNTERPART);
        counterpartDecidedAt = changedAt;
        changeStatus(TradeStatus.AWAITING_TEACHER, changedAt);
    }

    public void rejectByCounterpart(UUID actorStudentId, Instant changedAt) {
        requireCounterpart(actorStudentId);
        requireStatus(TradeStatus.AWAITING_COUNTERPART);
        counterpartDecidedAt = changedAt;
        changeStatus(TradeStatus.DECLINED, changedAt);
    }

    public void beginSettlement(
        UUID policySnapshotId,
        UUID transactionGroupId,
        Instant changedAt
    ) {
        requireStatus(TradeStatus.AWAITING_TEACHER);
        this.policySnapshotId = Objects.requireNonNull(
            policySnapshotId,
            "policySnapshotId must not be null"
        );
        this.transactionGroupId = transactionGroupId;
        this.teacherDecidedAt = changedAt;
        changeStatus(TradeStatus.SETTLING, changedAt);
    }

    public void complete(Instant changedAt) {
        requireStatus(TradeStatus.SETTLING);
        changeStatus(TradeStatus.COMPLETED, changedAt);
    }

    public void rejectByTeacher(Instant changedAt) {
        requireStatus(TradeStatus.AWAITING_TEACHER);
        teacherDecidedAt = changedAt;
        changeStatus(TradeStatus.REJECTED, changedAt);
    }

    private void requireCounterpart(UUID actorStudentId) {
        if (!counterpartStudentId.equals(actorStudentId)) {
            throw new TradeOperationException(
                "NOT_TRADE_COUNTERPART",
                "거래 상대 학생만 동의하거나 거절할 수 있습니다."
            );
        }
    }

    private void requireStatus(TradeStatus expected) {
        if (status != expected) {
            invalidTransition();
        }
    }

    private void invalidTransition() {
        throw new TradeOperationException(
            "INVALID_STATE_TRANSITION",
            "현재 거래 상태에서는 요청한 동작을 수행할 수 없습니다."
        );
    }

    private void changeStatus(TradeStatus next, Instant changedAt) {
        status = next;
        statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getProposerStudentId() {
        return proposerStudentId;
    }

    public UUID getCounterpartStudentId() {
        return counterpartStudentId;
    }

    public TradeStatus getStatus() {
        return status;
    }

    public UUID getPolicySnapshotId() {
        return policySnapshotId;
    }

    public UUID getTransactionGroupId() {
        return transactionGroupId;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    /** 학생이 적어 둔 거래 설명이다. 승인 화면에서 교사가 무슨 거래인지 알아보는 근거다. */
    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String normalizedDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
