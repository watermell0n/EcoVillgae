package com.b201.ecovillage.investment.domain;

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

/**
 * 학급의 가상 투자 시장이다.
 *
 * <p>investment는 예금·적금이 있는 finance와 분리된 독립 도메인이다. ledger는 매수·매도 정산에
 * 협력하되 투자 상태의 소유자가 아니다.
 *
 * <p>가격은 시나리오에 미리 정해져 있고 교사가 단계를 하나씩 공개한다. {@code currentStep}은
 * 지금까지 공개된 마지막 단계 번호다.
 */
@Entity
@Table(name = "investment_markets")
public class InvestmentMarket {

    public static final String DRAFT = "DRAFT";
    public static final String OPEN = "OPEN";
    public static final String PAUSED = "PAUSED";
    public static final String CLOSED = "CLOSED";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    /**
     * 이 시장이 운영하는 투자 방식이다. 학급은 단기와 장기를 동시에 운영할 수 있고 시장은
     * 방식마다 하나씩 있으므로, 한 번 정해진 뒤에는 바뀌지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "investment_mode", nullable = false, updatable = false, length = 20)
    private InvestmentMode investmentMode;

    @Column(name = "active_scenario_version_id")
    private UUID activeScenarioVersionId;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

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

    protected InvestmentMarket() {
    }

    public static InvestmentMarket draft(
        UUID id,
        UUID classroomId,
        InvestmentMode investmentMode,
        Instant now
    ) {
        InvestmentMarket market = new InvestmentMarket();
        market.id = Objects.requireNonNull(id);
        market.classroomId = Objects.requireNonNull(classroomId);
        market.investmentMode = Objects.requireNonNull(investmentMode);
        market.currentStep = 0;
        market.status = DRAFT;
        market.statusChangedAt = Objects.requireNonNull(now);
        market.createdAt = now;
        market.updatedAt = now;
        return market;
    }

    public void configure(UUID scenarioVersionId, Instant now) {
        if (!DRAFT.equals(status)) {
            throw new IllegalStateException("Only a draft market can be configured");
        }
        activeScenarioVersionId = Objects.requireNonNull(scenarioVersionId);
        currentStep = 0;
        updatedAt = Objects.requireNonNull(now);
    }

    public void start(Instant now) {
        if (!DRAFT.equals(status) || activeScenarioVersionId == null) {
            throw new IllegalStateException("A configured draft market is required");
        }
        currentStep = 1;
        status = OPEN;
        openedAt = Objects.requireNonNull(now);
        statusChangedAt = now;
        updatedAt = now;
    }

    /**
     * 다음 날의 가격 단계를 공개한 뒤 현재 단계를 한 칸 올린다.
     *
     * <p>공개할 단계가 실제로 있는지는 시나리오를 아는 쪽에서 먼저 확인한다. 이 메서드는
     * 시장 상태만 옮긴다.
     */
    public void advanceDay(Instant now) {
        if (!OPEN.equals(status)) {
            throw new IllegalStateException("Only an open market can advance to the next day");
        }
        currentStep += 1;
        updatedAt = Objects.requireNonNull(now);
    }

    /**
     * 정산까지 끝나 더 이상 거래하지 않는 시장이다. 방식별 유니크 조건은 CLOSED를 제외하므로
     * 정산이 끝난 뒤에는 같은 방식의 새 시장을 다시 열 수 있다(단기의 하루 주기).
     */
    public void close(Instant now) {
        if (CLOSED.equals(status)) {
            throw new IllegalStateException("The market is already closed");
        }
        status = CLOSED;
        closedAt = Objects.requireNonNull(now);
        statusChangedAt = now;
        updatedAt = now;
    }

    /**
     * 학생이 이 방식의 투자에 들어가지 못하게 잠근다. 시나리오와 학생 보유 내역은 그대로 남으므로
     * 다시 열 수 있다. 학급은 단기와 장기를 함께 운영하므로 잠금도 방식마다 따로 걸린다.
     */
    public void pauseForStudents(Instant now) {
        if (!OPEN.equals(status)) {
            throw new IllegalStateException("Only an open market can be paused");
        }
        status = PAUSED;
        statusChangedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void resumeForStudents(Instant now) {
        if (!PAUSED.equals(status)) {
            throw new IllegalStateException("Only a paused market can be resumed");
        }
        status = OPEN;
        statusChangedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    /**
     * 시나리오가 공개되어 학생이 시세를 볼 수 있는 시장이다.
     *
     * @param currentStep 공개된 마지막 단계 번호다. 아직 공개 전이면 0이다.
     */
    public static InvestmentMarket open(
        UUID id,
        UUID classroomId,
        InvestmentMode investmentMode,
        UUID activeScenarioVersionId,
        int currentStep,
        Instant now
    ) {
        if (currentStep < 0) {
            throw new IllegalArgumentException("currentStep must not be negative");
        }
        InvestmentMarket market = new InvestmentMarket();
        market.id = Objects.requireNonNull(id);
        market.classroomId = Objects.requireNonNull(classroomId);
        market.investmentMode = Objects.requireNonNull(investmentMode);
        market.activeScenarioVersionId = Objects.requireNonNull(activeScenarioVersionId);
        market.currentStep = currentStep;
        market.status = OPEN;
        market.openedAt = now;
        market.statusChangedAt = now;
        market.createdAt = now;
        market.updatedAt = now;
        return market;
    }

    /**
     * 학생이 볼 수 있는 시장인지 판단한다. 잠시 멈춘 시장은 잠금 상태를 알려 주고, 정산을
     * 마친 시장은 최종 손익을 보여 줘야 하므로 초안만 제외한다.
     */
    public boolean isReadable() {
        return !DRAFT.equals(status) && activeScenarioVersionId != null && currentStep >= 1;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public InvestmentMode getInvestmentMode() {
        return investmentMode;
    }

    public UUID getActiveScenarioVersionId() {
        return activeScenarioVersionId;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
