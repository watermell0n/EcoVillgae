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
import com.b201.ecovillage.investment.domain.InvestmentMode;
import com.b201.ecovillage.investment.domain.InvestmentModeConfig;
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

    /**
     * 한 방식의 시장을 돌려준다. 학급은 단기와 장기를 동시에 운영하므로 방식을 지정해야
     * 시장이 하나로 정해진다.
     */
    @Transactional(readOnly = true)
    public InvestmentMarketView find(String subject, UUID classroomId, String requestedMode) {
        boolean teacher = authorize(subject, classroomId);
        InvestmentMode mode = InvestmentModeConfig.parse(requestedMode)
            .orElseThrow(() -> operation(
                HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_MODE", "투자 방식이 올바르지 않습니다."
            ));
        InvestmentMarket market = marketRepository.findByModeLiveFirst(classroomId, mode).stream()
            .findFirst()
            .filter(candidate -> teacher || candidate.isReadable())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "아직 투자 시나리오가 없습니다."
            ));
        if (market.getActiveScenarioVersionId() == null) {
            throw operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "아직 투자 시나리오가 없습니다.");
        }
        return toView(classroomId, market, teacher);
    }

    /**
     * 학급이 운영하는 모든 방식의 시장을 돌려준다. 학생 화면은 단기와 장기를 함께 보여 주므로
     * 한 번에 받아야 한다.
     *
     * <p>시나리오가 아직 없는 방식은 목록에서 빠진다. 학생에게는 공개된 시장만 나간다.
     */
    @Transactional(readOnly = true)
    public List<InvestmentMarketView> findAll(String subject, UUID classroomId) {
        boolean teacher = authorize(subject, classroomId);
        return marketRepository.findAllLiveByClassroomId(classroomId).stream()
            .filter(market -> market.getActiveScenarioVersionId() != null)
            .filter(market -> teacher || market.isReadable())
            .map(market -> toView(classroomId, market, teacher))
            .toList();
    }

    private boolean authorize(String subject, UUID classroomId) {
        boolean teacher = userRepository.findByExternalSubject(subject).isPresent();
        UUID studentId = teacher ? null : parseStudentId(subject);
        authorizationService.authorize(
            subject, classroomId, ClassroomPermission.READ_INVESTMENT_MARKET, studentId
        );
        return teacher;
    }

    private InvestmentMarketView toView(UUID classroomId, InvestmentMarket market, boolean teacher) {
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
        // 초안 시나리오는 학생에게 아직 보이지 않으므로 교사가 전체 가격 경로를 검토할 수 있다.
        boolean previewPlannedPath = teacher && InvestmentMarket.DRAFT.equals(market.getStatus());
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
                asset.getMinReturnBps(), asset.getMaxReturnBps(),
                current == null ? null : current.getPrice(),
                current == null ? null : current.getStepNumber(),
                history.stream().map(InvestmentMarketQueryService::toPricePoint).toList(),
                previewPlannedPath
                    ? priceStepRepository
                        .findAllByClassroomIdAndInvestmentAssetIdOrderByStepNumber(
                            classroomId, asset.getId()
                        )
                        .stream().map(InvestmentMarketQueryService::toPricePoint).toList()
                    : List.of(),
                news
            );
        }).toList();
        // 학생에게는 교사가 이야기를 승인한 종목만 보입니다. 승인 전에 종목과 시세가 먼저
        // 보이면 "승인·공개"가 공개를 결정한다는 뜻이 깨지고, 학생은 근거 없이 투자하게 됩니다.
        if (!teacher) {
            views = views.stream().filter(asset -> !asset.news().isEmpty()).toList();
        }
        return new InvestmentMarketView(
            market.getId(), scenario.getId(), market.getStatus(), market.getInvestmentMode().name(),
            scenario.getScenarioSource().name(), market.getInvestmentMode().settlementRule(),
            market.getCurrentStep(), market.getStateVersion(), teacher, views
        );
    }

    private static InvestmentMarketView.PricePoint toPricePoint(InvestmentPriceStep step) {
        return new InvestmentMarketView.PricePoint(
            step.getStepNumber(), step.getPrice(), step.getChangeRateBps(), step.getRevealedAt()
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
