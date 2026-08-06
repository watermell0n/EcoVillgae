package com.b201.ecovillage.investment.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.investment.domain.InvestmentAsset;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentPriceStepRepository;
import com.b201.ecovillage.investment.repository.InvestmentScenarioVersionRepository;

/**
 * AI 투자 뉴스가 쓸 수 있는 공개 시세만 골라 조회하는 진입점이다.
 *
 * <p>다른 도메인은 이 조회만 쓰고 투자 엔티티를 직접 다루지 않는다. 미공개 가격 단계는 어떤
 * 경로로도 나가지 않는다.
 *
 * <p>이 단계는 조회 전용이다. 학생 매수·매도와 단계 진행은 투자 도메인 2·3단계에서 만든다.
 */
@Service
public class InvestmentNewsTargetQuery {

    private final InvestmentMarketRepository marketRepository;
    private final InvestmentAssetRepository assetRepository;
    private final InvestmentPriceStepRepository priceStepRepository;
    private final InvestmentScenarioVersionRepository scenarioRepository;

    public InvestmentNewsTargetQuery(
        InvestmentMarketRepository marketRepository,
        InvestmentAssetRepository assetRepository,
        InvestmentPriceStepRepository priceStepRepository,
        InvestmentScenarioVersionRepository scenarioRepository
    ) {
        this.marketRepository = marketRepository;
        this.assetRepository = assetRepository;
        this.priceStepRepository = priceStepRepository;
        this.scenarioRepository = scenarioRepository;
    }

    /**
     * 학급에서 지금 뉴스를 붙일 수 있는 종목을 표시 순서대로 돌려준다.
     *
     * <p>학급은 단기와 장기를 함께 운영하므로 열려 있는 두 시장의 종목이 모두 담긴다.
     * 어느 방식의 종목인지는 결과의 {@code investmentMode}로 구분한다.
     *
     * <p>공개 단계가 없거나 시장이 열려 있지 않으면 빈 목록이다. 공개 이력이 1단계부터
     * 현재 단계까지 이어지지 않는 종목은 AI 계약을 만족할 수 없어 제외한다.
     */
    @Transactional(readOnly = true)
    public List<InvestmentNewsTargetView> findPublishableTargets(UUID classroomId) {
        List<InvestmentNewsTargetView> targets = new ArrayList<>();
        for (InvestmentMarket market : marketRepository
            .findAllByClassroomIdAndStatus(classroomId, InvestmentMarket.OPEN)) {
            if (!market.isReadable()) {
                continue;
            }
            InvestmentScenarioVersion scenario = publishedScenario(classroomId, market);
            if (scenario == null) {
                continue;
            }
            for (InvestmentAsset asset : assetRepository
                .findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
                    classroomId,
                    market.getActiveScenarioVersionId(),
                    InvestmentAsset.ACTIVE
                )) {
                toTarget(classroomId, market, scenario, asset).ifPresent(targets::add);
            }
        }
        return List.copyOf(targets);
    }

    private InvestmentScenarioVersion publishedScenario(UUID classroomId, InvestmentMarket market) {
        return scenarioRepository
            .findByClassroomIdAndId(classroomId, market.getActiveScenarioVersionId())
            .filter(candidate -> InvestmentScenarioVersion.PUBLISHED.equals(candidate.getStatus()))
            .orElse(null);
    }

    /**
     * 특정 종목의 현재 공개 단계를 돌려준다. 초안 생성과 승인 양쪽에서 같은 기준을 쓴다.
     */
    @Transactional(readOnly = true)
    public Optional<InvestmentNewsTargetView> findPublishableTarget(
        UUID classroomId,
        UUID assetId
    ) {
        // 종목이 단기·장기 중 어느 시장의 것인지는 종목이 속한 시나리오로 되짚어 찾는다.
        InvestmentAsset asset = assetRepository.findByClassroomIdAndId(classroomId, assetId)
            .filter(InvestmentAsset::isActive)
            .orElse(null);
        if (asset == null) {
            return Optional.empty();
        }
        InvestmentMarket market = marketRepository
            .findByClassroomIdAndActiveScenarioVersionId(classroomId, asset.getScenarioVersionId())
            .filter(candidate -> InvestmentMarket.OPEN.equals(candidate.getStatus()))
            .filter(InvestmentMarket::isReadable)
            .orElse(null);
        if (market == null) {
            return Optional.empty();
        }
        InvestmentScenarioVersion scenario = publishedScenario(classroomId, market);
        if (scenario == null) {
            return Optional.empty();
        }
        return toTarget(classroomId, market, scenario, asset);
    }

    private Optional<InvestmentNewsTargetView> toTarget(
        UUID classroomId,
        InvestmentMarket market,
        InvestmentScenarioVersion scenario,
        InvestmentAsset asset
    ) {
        List<InvestmentPriceStep> revealed = priceStepRepository
            .findAllByClassroomIdAndInvestmentAssetIdAndRevealedAtIsNotNullOrderByStepNumber(
                classroomId,
                asset.getId()
            );
        if (!coversFirstStepToCurrent(revealed, market.getCurrentStep())) {
            return Optional.empty();
        }
        InvestmentPriceStep currentStep = revealed.get(revealed.size() - 1);
        InvestmentPriceStep targetStep = priceStepRepository
            .findByClassroomIdAndInvestmentAssetIdAndStepNumber(
                classroomId,
                asset.getId(),
                market.getCurrentStep() + 1
            )
            .filter(step -> !step.isRevealed())
            .orElse(null);
        if (targetStep == null) {
            return Optional.empty();
        }
        List<InvestmentNewsTargetView.PriceFact> facts = revealed.stream()
            .map(step -> new InvestmentNewsTargetView.PriceFact(
                step.getStepNumber(),
                step.getPrice(),
                step.getChangeRateBps(),
                step.getRevealedAt()
            ))
            .toList();
        return Optional.of(new InvestmentNewsTargetView(
            market.getActiveScenarioVersionId(),
            asset.getId(),
            currentStep.getId(),
            targetStep.getId(),
            asset.getName(),
            asset.getSymbol(),
            asset.getFictionalCompanyProfile(),
            market.getInvestmentMode().name(),
            scenario.getScenarioSource().name(),
            market.getInvestmentMode().settlementRule(),
            currentStep.getStepNumber(),
            targetStep.getStepNumber(),
            direction(targetStep.getChangeRateBps()),
            historicalReference(scenario),
            facts
        ));
    }

    private static String direction(int changeRateBps) {
        if (changeRateBps > 0) {
            return "RISE";
        }
        if (changeRateBps < 0) {
            return "FALL";
        }
        return "FLAT";
    }

    private static InvestmentNewsTargetView.HistoricalIndexReference historicalReference(
        InvestmentScenarioVersion scenario
    ) {
        com.b201.ecovillage.investment.domain.HistoricalIndexReference reference =
            scenario.getHistoricalIndexReference();
        if (reference == null) {
            return null;
        }
        return new InvestmentNewsTargetView.HistoricalIndexReference(
            reference.seriesCode(),
            reference.eventName(),
            reference.sourceName(),
            reference.sourceUrl(),
            reference.periodStart(),
            reference.periodEnd()
        );
    }

    /**
     * AI 계약은 1단계부터 현재 단계까지 빠짐없이 오름차순인 공개 이력을 요구한다. 공개 시각도
     * 단계 순서대로 증가해야 한다. 조건을 만족하지 못하는 종목은 뉴스 대상에서 뺀다.
     */
    private static boolean coversFirstStepToCurrent(
        List<InvestmentPriceStep> revealed,
        int currentStep
    ) {
        if (revealed.isEmpty() || revealed.size() != currentStep) {
            return false;
        }
        for (int index = 0; index < revealed.size(); index++) {
            if (revealed.get(index).getStepNumber() != index + 1) {
                return false;
            }
            if (index > 0 && !revealed.get(index).getRevealedAt()
                .isAfter(revealed.get(index - 1).getRevealedAt())) {
                return false;
            }
        }
        return true;
    }
}
