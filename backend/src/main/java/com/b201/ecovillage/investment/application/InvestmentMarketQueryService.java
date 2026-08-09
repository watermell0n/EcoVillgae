package com.b201.ecovillage.investment.application;

import java.util.Collection;
import java.util.LinkedHashMap;
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
import com.b201.ecovillage.investment.domain.InvestmentOrder;
import com.b201.ecovillage.investment.domain.InvestmentPosition;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentOrderRepository;
import com.b201.ecovillage.investment.repository.InvestmentPositionRepository;
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
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentOrderRepository orderRepository;

    public InvestmentMarketQueryService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        InvestmentMarketRepository marketRepository,
        InvestmentScenarioVersionRepository scenarioRepository,
        InvestmentAssetRepository assetRepository,
        InvestmentPriceStepRepository priceStepRepository,
        EconomicEventDraftRepository eventRepository,
        InvestmentPositionRepository positionRepository,
        InvestmentOrderRepository orderRepository
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.marketRepository = marketRepository;
        this.scenarioRepository = scenarioRepository;
        this.assetRepository = assetRepository;
        this.priceStepRepository = priceStepRepository;
        this.eventRepository = eventRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 한 방식의 시장을 돌려준다. 학급은 단기와 장기를 동시에 운영하므로 방식을 지정해야
     * 시장이 하나로 정해진다.
     */
    @Transactional(readOnly = true)
    public InvestmentMarketView find(String subject, UUID classroomId, String requestedMode) {
        Access access = authorize(subject, classroomId);
        InvestmentMode mode = InvestmentModeConfig.parse(requestedMode)
            .orElseThrow(() -> operation(
                HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_MODE", "투자 방식이 올바르지 않습니다."
            ));
        InvestmentMarket market = marketRepository.findByModeLiveFirst(classroomId, mode).stream()
            .filter(candidate -> access.teacher() || candidate.isReadable())
            .findFirst()
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "아직 투자 시나리오가 없습니다."
            ));
        if (market.getActiveScenarioVersionId() == null) {
            throw operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "아직 투자 시나리오가 없습니다.");
        }
        return toView(classroomId, market, access);
    }

    /**
     * 학급이 운영하는 모든 방식의 시장을 돌려준다. 학생 화면은 단기와 장기를 함께 보여 주므로
     * 한 번에 받아야 한다.
     *
     * <p>시나리오가 아직 없는 방식은 목록에서 빠진다. 학생에게는 공개된 시장만 나간다.
     */
    @Transactional(readOnly = true)
    public List<InvestmentMarketView> findAll(String subject, UUID classroomId) {
        Access access = authorize(subject, classroomId);
        List<InvestmentMarket> markets = access.teacher()
            ? marketRepository.findAllLiveByClassroomId(classroomId)
            : latestStudentMarkets(classroomId);
        return markets.stream()
            .filter(market -> market.getActiveScenarioVersionId() != null)
            .filter(market -> access.teacher() || market.isReadable())
            .map(market -> toView(classroomId, market, access))
            .toList();
    }

    /** All executed orders for the signed-in student, including closed markets. */
    @Transactional(readOnly = true)
    public List<InvestmentOrderHistoryView> findOrderHistory(String subject, UUID classroomId) {
        Access access = authorize(subject, classroomId);
        if (access.studentId() == null) {
            throw operation(HttpStatus.FORBIDDEN, "ROLE_NOT_ALLOWED", "학생 투자 내역입니다.");
        }
        List<InvestmentOrder> orders = orderRepository
            .findAllByClassroomIdAndClassroomStudentIdOrderBySubmittedAtDesc(
                classroomId,
                access.studentId()
            );
        Map<UUID, InvestmentAsset> assets = assetRepository.findAllById(
            orders.stream().map(InvestmentOrder::getInvestmentAssetId).distinct().toList()
        ).stream().collect(Collectors.toMap(InvestmentAsset::getId, asset -> asset));
        Map<UUID, InvestmentScenarioVersion> scenarios = scenarioRepository.findAllById(
            assets.values().stream().map(InvestmentAsset::getScenarioVersionId).distinct().toList()
        ).stream().collect(Collectors.toMap(InvestmentScenarioVersion::getId, scenario -> scenario));

        return orders.stream().map(order -> {
            InvestmentAsset asset = assets.get(order.getInvestmentAssetId());
            InvestmentScenarioVersion scenario = asset == null
                ? null
                : scenarios.get(asset.getScenarioVersionId());
            long total = Math.multiplyExact(order.getExecutedPrice(), order.getQuantity());
            return new InvestmentOrderHistoryView(
                order.getId(),
                order.getInvestmentAssetId(),
                asset == null ? "-" : asset.getSymbol(),
                asset == null ? "종목 정보 없음" : asset.getName(),
                scenario == null ? "UNKNOWN" : scenario.getInvestmentMode().name(),
                order.getSide(),
                order.getQuantity(),
                order.getExecutedPrice(),
                total,
                order.getExecutedPriceStep(),
                order.getStatus(),
                order.getExecutedAt()
            );
        }).toList();
    }

    /** 각 방식에서 학생에게 공개된 가장 최근 시장 하나를 남긴다. 새 초안이 생겨도 직전 정산
     * 결과는 새 시장이 실제로 시작할 때까지 계속 볼 수 있어야 한다. */
    private List<InvestmentMarket> latestStudentMarkets(UUID classroomId) {
        Map<InvestmentMode, InvestmentMarket> latest = new LinkedHashMap<>();
        marketRepository.findAllByClassroomIdOrderByCreatedAtDesc(classroomId).stream()
            .filter(InvestmentMarket::isReadable)
            .forEach(market -> latest.putIfAbsent(market.getInvestmentMode(), market));
        return List.copyOf(latest.values());
    }

    private Access authorize(String subject, UUID classroomId) {
        boolean teacher = userRepository.findByExternalSubject(subject).isPresent();
        UUID studentId = teacher ? null : parseStudentId(subject);
        authorizationService.authorize(
            subject, classroomId, ClassroomPermission.READ_INVESTMENT_MARKET, studentId
        );
        return new Access(teacher, studentId);
    }

    private InvestmentMarketView toView(UUID classroomId, InvestmentMarket market, Access access) {
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
        boolean previewPlannedPath = access.teacher() && InvestmentMarket.DRAFT.equals(market.getStatus());
        List<UUID> revealedStepIds = revealedByAsset.values().stream()
            .flatMap(Collection::stream)
            .map(InvestmentPriceStep::getId)
            .toList();
        List<String> visibleStatuses = access.teacher()
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
            Holdings holdings = holdings(
                classroomId, access.studentId(), asset.getId(), current == null ? null : current.getPrice()
            );
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
                news,
                holdings.quantity(),
                holdings.averagePrice(),
                holdings.currentValue(),
                holdings.totalBoughtAmount(),
                holdings.totalSoldAmount(),
                holdings.profitLoss()
            );
        }).toList();
        // 학생에게는 교사가 이야기를 승인한 종목만 보입니다. 승인 전에 종목과 시세가 먼저
        // 보이면 "승인·공개"가 공개를 결정한다는 뜻이 깨지고, 학생은 근거 없이 투자하게 됩니다.
        if (!access.teacher() && !InvestmentMarket.CLOSED.equals(market.getStatus())) {
            views = views.stream().filter(asset -> !asset.news().isEmpty()).toList();
        }
        return new InvestmentMarketView(
            market.getId(), scenario.getId(), market.getStatus(), market.getInvestmentMode().name(),
            scenario.getScenarioSource().name(), market.getInvestmentMode().settlementRule(),
            market.getCurrentStep(), market.getStateVersion(), access.teacher(), views
        );
    }

    private Holdings holdings(UUID classroomId, UUID studentId, UUID assetId, Long currentPrice) {
        if (studentId == null) {
            return Holdings.empty();
        }
        InvestmentPosition position = positionRepository
            .findByClassroomIdAndClassroomStudentIdAndInvestmentAssetId(
                classroomId, studentId, assetId
            )
            .orElse(null);
        int quantity = position == null ? 0 : position.getQuantity();
        long averagePrice = position == null ? 0L : position.getAveragePrice();
        long currentValue = currentPrice == null ? 0L : Math.multiplyExact(currentPrice, quantity);
        long bought = 0L;
        long sold = 0L;
        for (InvestmentOrder order : orderRepository
            .findAllByClassroomIdAndClassroomStudentIdAndInvestmentAssetIdOrderBySubmittedAtAsc(
                classroomId, studentId, assetId
            )) {
            long amount = Math.multiplyExact(order.getExecutedPrice(), order.getQuantity());
            if ("BUY".equals(order.getSide())) {
                bought = Math.addExact(bought, amount);
            } else if ("SELL".equals(order.getSide())) {
                sold = Math.addExact(sold, amount);
            }
        }
        long profitLoss = Math.subtractExact(Math.addExact(sold, currentValue), bought);
        return new Holdings(quantity, averagePrice, currentValue, bought, sold, profitLoss);
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

    private record Access(boolean teacher, UUID studentId) {
    }

    private record Holdings(
        int quantity,
        long averagePrice,
        long currentValue,
        long totalBoughtAmount,
        long totalSoldAmount,
        long profitLoss
    ) {
        static Holdings empty() {
            return new Holdings(0, 0L, 0L, 0L, 0L, 0L);
        }
    }
}
