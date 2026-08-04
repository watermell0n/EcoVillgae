package com.b201.ecovillage.investment.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.domain.EconomicEventDraft;
import com.b201.ecovillage.ai.repository.EconomicEventDraftRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.investment.domain.InvestmentAsset;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentPriceStepRepository;
import com.b201.ecovillage.investment.repository.InvestmentScenarioVersionRepository;

@Service
public class InvestmentMarketQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final InvestmentMarketRepository marketRepository;
    private final InvestmentScenarioVersionRepository scenarioRepository;
    private final InvestmentAssetRepository assetRepository;
    private final InvestmentPriceStepRepository priceStepRepository;
    private final EconomicEventDraftRepository eventRepository;

    public InvestmentMarketQueryService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        InvestmentMarketRepository marketRepository,
        InvestmentScenarioVersionRepository scenarioRepository,
        InvestmentAssetRepository assetRepository,
        InvestmentPriceStepRepository priceStepRepository,
        EconomicEventDraftRepository eventRepository
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.marketRepository = marketRepository;
        this.scenarioRepository = scenarioRepository;
        this.assetRepository = assetRepository;
        this.priceStepRepository = priceStepRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public InvestmentMarketView find(String subject, UUID classroomId) {
        boolean teacher = userRepository.findByExternalSubject(subject).isPresent();
        UUID studentId = teacher ? null : parseStudentId(subject);
        authorizationService.authorize(
            subject, classroomId, ClassroomPermission.READ_INVESTMENT_MARKET, studentId
        );
        InvestmentMarket market = marketRepository.findByClassroomId(classroomId)
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "아직 투자 시나리오가 없습니다."));
        if (market.getActiveScenarioVersionId() == null) {
            throw operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "아직 투자 시나리오가 없습니다.");
        }
        InvestmentScenarioVersion scenario = scenarioRepository
            .findByClassroomIdAndId(classroomId, market.getActiveScenarioVersionId())
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "INVALID_PRICE_STEP", "투자 시나리오를 찾을 수 없습니다."));
        List<InvestmentAsset> assets = assetRepository
            .findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
                classroomId, scenario.getId(), InvestmentAsset.ACTIVE
            );
        Map<UUID, List<InvestmentPriceStep>> revealedByAsset = assets.stream().collect(Collectors.toMap(
            InvestmentAsset::getId,
            asset -> priceStepRepository
                .findAllByClassroomIdAndInvestmentAssetIdAndRevealedAtIsNotNullOrderByStepNumber(
                    classroomId, asset.getId()
                )
        ));
        List<UUID> revealedStepIds = revealedByAsset.values().stream()
            .flatMap(Collection::stream)
            .map(InvestmentPriceStep::getId)
            .toList();
        List<String> visibleStatuses = teacher
            ? List.of("DRAFT", "APPROVED", "APPLIED")
            : List.of("APPROVED", "APPLIED");
        List<EconomicEventDraft> events = revealedStepIds.isEmpty()
            ? List.of()
            : eventRepository.findAllByClassroomIdAndInvestmentPriceStepIdInAndStatusIn(
                classroomId, revealedStepIds, visibleStatuses
            );
        Map<UUID, List<EconomicEventDraft>> eventsByStep = events.stream()
            .collect(Collectors.groupingBy(EconomicEventDraft::getInvestmentPriceStepId));
        List<InvestmentMarketView.Asset> views = assets.stream().map(asset -> {
            List<InvestmentPriceStep> history = revealedByAsset.get(asset.getId());
            InvestmentPriceStep current = history.isEmpty() ? null : history.get(history.size() - 1);
            List<InvestmentMarketView.News> news = current == null ? List.of()
                : eventsByStep.getOrDefault(current.getId(), List.of()).stream()
                    .map(event -> new InvestmentMarketView.News(
                        event.getId(), event.getTitle(), event.getContent(), event.getStatus(),
                        event.getStateVersion(), event.getRevealedAt()
                    )).toList();
            return new InvestmentMarketView.Asset(
                asset.getId(), asset.getSymbol(), asset.getName(), asset.getFictionalCompanyProfile(),
                current == null ? null : current.getPrice(),
                current == null ? null : current.getStepNumber(),
                history.stream().map(step -> new InvestmentMarketView.PricePoint(
                    step.getStepNumber(), step.getPrice(), step.getChangeRateBps(), step.getRevealedAt()
                )).toList(),
                news
            );
        }).toList();
        return new InvestmentMarketView(
            market.getId(), scenario.getId(), market.getStatus(), scenario.getInvestmentMode().name(),
            scenario.getScenarioSource().name(), scenario.getInvestmentMode().settlementRule(),
            market.getCurrentStep(), market.getStateVersion(), teacher, views
        );
    }

    private static UUID parseStudentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "학생 인증이 필요합니다.");
        }
    }

    private static ClassroomOperationException operation(HttpStatus status, String code, String message) {
        return new ClassroomOperationException(status, code, message);
    }
}
