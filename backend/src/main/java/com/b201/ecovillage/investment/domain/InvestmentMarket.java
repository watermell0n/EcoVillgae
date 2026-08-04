package com.b201.ecovillage.investment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    public static InvestmentMarket draft(UUID id, UUID classroomId, Instant now) {
        InvestmentMarket market = new InvestmentMarket();
        market.id = Objects.requireNonNull(id);
        market.classroomId = Objects.requireNonNull(classroomId);
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
     * 시나리오가 공개되어 학생이 시세를 볼 수 있는 시장이다.
     *
     * @param currentStep 공개된 마지막 단계 번호다. 아직 공개 전이면 0이다.
     */
    public static InvestmentMarket open(
        UUID id,
        UUID classroomId,
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
     * 학생과 AI가 볼 수 있는 시장인지 판단한다. 시나리오가 없거나 공개 단계가 없으면 아직 아니다.
     */
    public boolean isReadable() {
        return OPEN.equals(status) && activeScenarioVersionId != null && currentStep >= 1;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
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
