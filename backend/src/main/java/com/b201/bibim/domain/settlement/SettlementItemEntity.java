package com.b201.bibim.domain.settlement;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settlement_items")
public class SettlementItemEntity {

    @Id
    private UUID id;

    @Column(name = "settlement_run_id", nullable = false)
    private UUID settlementRunId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "quiz_submitted", nullable = false)
    private boolean quizSubmitted;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "role_income", nullable = false)
    private long roleIncome;

    @Column(name = "participation_reward", nullable = false)
    private long participationReward;

    @Column(name = "correct_reward", nullable = false)
    private long correctReward;

    @Column(name = "gross_income", nullable = false)
    private long grossIncome;

    @Column(name = "income_tax", nullable = false)
    private long incomeTax;

    @Column(name = "net_income", nullable = false)
    private long netIncome;

    @Column(name = "attempt_type", nullable = false, length = 20)
    private String attemptType;

    @Column(name = "reward_rate_bps", nullable = false)
    private int rewardRateBps;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SettlementItemEntity() {
    }

    public static SettlementItemEntity create(
            UUID settlementRunId,
            UUID studentId,
            boolean quizSubmitted,
            int correctCount,
            long roleIncome,
            long participationReward,
            long correctReward,
            long grossIncome,
            long incomeTax,
            long netIncome
    ) {
        return create(
                settlementRunId, studentId, quizSubmitted, correctCount,
                roleIncome, participationReward, correctReward, grossIncome,
                incomeTax, netIncome, "LIVE", 10_000
        );
    }

    public static SettlementItemEntity create(
            UUID settlementRunId,
            UUID studentId,
            boolean quizSubmitted,
            int correctCount,
            long roleIncome,
            long participationReward,
            long correctReward,
            long grossIncome,
            long incomeTax,
            long netIncome,
            String attemptType,
            int rewardRateBps
    ) {
        SettlementItemEntity item = new SettlementItemEntity();
        item.id = UUID.randomUUID();
        item.settlementRunId = settlementRunId;
        item.studentId = studentId;
        item.quizSubmitted = quizSubmitted;
        item.correctCount = correctCount;
        item.roleIncome = roleIncome;
        item.participationReward = participationReward;
        item.correctReward = correctReward;
        item.grossIncome = grossIncome;
        item.incomeTax = incomeTax;
        item.netIncome = netIncome;
        item.attemptType = attemptType;
        item.rewardRateBps = rewardRateBps;
        item.createdAt = Instant.now();
        return item;
    }

    public UUID getSettlementRunId() {
        return settlementRunId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public boolean isQuizSubmitted() {
        return quizSubmitted;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public long getRoleIncome() {
        return roleIncome;
    }

    public long getParticipationReward() {
        return participationReward;
    }

    public long getCorrectReward() {
        return correctReward;
    }

    public long getGrossIncome() {
        return grossIncome;
    }

    public long getIncomeTax() {
        return incomeTax;
    }

    public long getNetIncome() {
        return netIncome;
    }

    public String getAttemptType() { return attemptType; }

    public int getRewardRateBps() { return rewardRateBps; }
}
