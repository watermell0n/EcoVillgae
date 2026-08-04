package com.b201.ecovillage.investment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 시나리오에 미리 정해진 가격 단계 하나다. 교사가 공개하기 전까지 학생과 AI에게 보이지 않는다.
 *
 * <p>단계 번호는 1부터 센다. DB의 CHECK 제약은 0 이상을 허용하지만 AI 계약과 명세 문서가
 * 첫 단계를 1로 보므로 이 팩토리에서 1 이상만 만든다. 나중에 마이그레이션으로 CHECK를
 * 1 이상으로 조이면 두 규칙이 완전히 일치한다.
 */
@Entity
@Table(name = "investment_price_steps")
public class InvestmentPriceStep {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "investment_asset_id", nullable = false, updatable = false)
    private UUID investmentAssetId;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(nullable = false)
    private long price;

    @Column(name = "change_rate_bps", nullable = false)
    private int changeRateBps;

    @Column(name = "revealed_at")
    private Instant revealedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvestmentPriceStep() {
    }

    /**
     * 아직 공개하지 않은 단계다. 시나리오를 만들 때 미리 쌓아둔다.
     */
    public static InvestmentPriceStep hidden(
        UUID id,
        UUID classroomId,
        UUID investmentAssetId,
        int stepNumber,
        long price,
        int changeRateBps,
        Instant now
    ) {
        if (stepNumber < 1) {
            throw new IllegalArgumentException("stepNumber starts at 1");
        }
        if (price < 1L) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (changeRateBps < -10_000 || changeRateBps > 100_000) {
            throw new IllegalArgumentException("changeRateBps must be between -10000 and 100000");
        }
        InvestmentPriceStep step = new InvestmentPriceStep();
        step.id = Objects.requireNonNull(id);
        step.classroomId = Objects.requireNonNull(classroomId);
        step.investmentAssetId = Objects.requireNonNull(investmentAssetId);
        step.stepNumber = stepNumber;
        step.price = price;
        step.changeRateBps = changeRateBps;
        step.createdAt = now;
        return step;
    }

    /**
     * 교사가 이미 공개한 단계다. 학생과 AI가 볼 수 있다.
     */
    public static InvestmentPriceStep revealed(
        UUID id,
        UUID classroomId,
        UUID investmentAssetId,
        int stepNumber,
        long price,
        int changeRateBps,
        Instant revealedAt
    ) {
        InvestmentPriceStep step = hidden(
            id,
            classroomId,
            investmentAssetId,
            stepNumber,
            price,
            changeRateBps,
            revealedAt
        );
        step.revealedAt = Objects.requireNonNull(revealedAt, "revealedAt must not be null");
        return step;
    }

    public boolean isRevealed() {
        return revealedAt != null;
    }

    public void reveal(Instant now) {
        if (revealedAt != null) {
            throw new IllegalStateException("The investment price step is already revealed");
        }
        revealedAt = Objects.requireNonNull(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvestmentAssetId() {
        return investmentAssetId;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public long getPrice() {
        return price;
    }

    public int getChangeRateBps() {
        return changeRateBps;
    }

    public Instant getRevealedAt() {
        return revealedAt;
    }
}
