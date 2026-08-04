package com.b201.ecovillage.investment.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 시장에 적용된 가격 시나리오의 한 판본이다. 가격 변동표는 이 판본에 묶여 고정된다.
 */
@Entity
@Table(name = "investment_scenario_versions")
public class InvestmentScenarioVersion {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "investment_market_id", nullable = false, updatable = false)
    private UUID investmentMarketId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "scenario_seed", length = 128)
    private String scenarioSeed;

    @Column(name = "investment_mode", nullable = false, length = 20)
    private String investmentMode;

    @Column(name = "scenario_source", nullable = false, length = 30)
    private String scenarioSource;

    @Column(name = "reference_series_code", length = 40)
    private String referenceSeriesCode;

    @Column(name = "reference_event_name", length = 120)
    private String referenceEventName;

    @Column(name = "reference_source_name", length = 120)
    private String referenceSourceName;

    @Column(name = "reference_source_url", length = 500)
    private String referenceSourceUrl;

    @Column(name = "reference_period_start")
    private LocalDate referencePeriodStart;

    @Column(name = "reference_period_end")
    private LocalDate referencePeriodEnd;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvestmentScenarioVersion() {
    }

    public static InvestmentScenarioVersion draft(
        UUID id,
        UUID classroomId,
        UUID investmentMarketId,
        int version,
        String name,
        InvestmentMode investmentMode,
        InvestmentScenarioSource scenarioSource,
        HistoricalIndexReference historicalIndexReference,
        Instant now
    ) {
        InvestmentScenarioVersion scenario = build(
            id, classroomId, investmentMarketId, version, name, investmentMode,
            scenarioSource, historicalIndexReference, now
        );
        scenario.status = DRAFT;
        scenario.publishedAt = null;
        return scenario;
    }

    public void publish(Instant now) {
        if (!DRAFT.equals(status)) {
            throw new IllegalStateException("Only a draft scenario can be published");
        }
        status = PUBLISHED;
        publishedAt = Objects.requireNonNull(now);
    }

    public static InvestmentScenarioVersion published(
        UUID id,
        UUID classroomId,
        UUID investmentMarketId,
        int version,
        String name,
        Instant now
    ) {
        return published(
            id,
            classroomId,
            investmentMarketId,
            version,
            name,
            InvestmentMode.LONG_TERM,
            InvestmentScenarioSource.AI_GENERATED,
            null,
            now
        );
    }

    public static InvestmentScenarioVersion published(
        UUID id,
        UUID classroomId,
        UUID investmentMarketId,
        int version,
        String name,
        InvestmentMode investmentMode,
        InvestmentScenarioSource scenarioSource,
        HistoricalIndexReference historicalIndexReference,
        Instant now
    ) {
        InvestmentScenarioVersion scenario = build(
            id, classroomId, investmentMarketId, version, name, investmentMode,
            scenarioSource, historicalIndexReference, now
        );
        scenario.status = PUBLISHED;
        scenario.publishedAt = now;
        return scenario;
    }

    private static InvestmentScenarioVersion build(
        UUID id,
        UUID classroomId,
        UUID investmentMarketId,
        int version,
        String name,
        InvestmentMode investmentMode,
        InvestmentScenarioSource scenarioSource,
        HistoricalIndexReference historicalIndexReference,
        Instant now
    ) {
        Objects.requireNonNull(investmentMode, "investmentMode must not be null");
        Objects.requireNonNull(scenarioSource, "scenarioSource must not be null");
        if (investmentMode == InvestmentMode.SHORT_TERM) {
            if (
                scenarioSource != InvestmentScenarioSource.AI_GENERATED
                    || historicalIndexReference != null
            ) {
                throw new IllegalArgumentException(
                    "Short-term investment must use an AI-generated scenario"
                );
            }
        } else if (
            (scenarioSource == InvestmentScenarioSource.HISTORICAL_INDEX)
                != (historicalIndexReference != null)
        ) {
            throw new IllegalArgumentException(
                "Historical long-term scenarios require exactly one index reference"
            );
        }
        InvestmentScenarioVersion scenario = new InvestmentScenarioVersion();
        scenario.id = Objects.requireNonNull(id);
        scenario.classroomId = Objects.requireNonNull(classroomId);
        scenario.investmentMarketId = Objects.requireNonNull(investmentMarketId);
        scenario.version = version;
        scenario.name = Objects.requireNonNull(name);
        scenario.investmentMode = investmentMode.name();
        scenario.scenarioSource = scenarioSource.name();
        if (historicalIndexReference != null) {
            scenario.referenceSeriesCode = historicalIndexReference.seriesCode();
            scenario.referenceEventName = historicalIndexReference.eventName();
            scenario.referenceSourceName = historicalIndexReference.sourceName();
            scenario.referenceSourceUrl = historicalIndexReference.sourceUrl();
            scenario.referencePeriodStart = historicalIndexReference.periodStart();
            scenario.referencePeriodEnd = historicalIndexReference.periodEnd();
        }
        scenario.createdAt = now;
        return scenario;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvestmentMarketId() {
        return investmentMarketId;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public InvestmentMode getInvestmentMode() {
        return InvestmentMode.valueOf(investmentMode);
    }

    public InvestmentScenarioSource getScenarioSource() {
        return InvestmentScenarioSource.valueOf(scenarioSource);
    }

    public HistoricalIndexReference getHistoricalIndexReference() {
        if (getScenarioSource() != InvestmentScenarioSource.HISTORICAL_INDEX) {
            return null;
        }
        return new HistoricalIndexReference(
            referenceSeriesCode,
            referenceEventName,
            referenceSourceName,
            referenceSourceUrl,
            referencePeriodStart,
            referencePeriodEnd
        );
    }
}
