package com.b201.ecovillage.investment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.investment.domain.HistoricalIndexReference;
import com.b201.ecovillage.investment.domain.InvestmentAsset;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentMode;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.domain.InvestmentScenarioSource;
import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentPriceStepRepository;
import com.b201.ecovillage.investment.repository.InvestmentScenarioVersionRepository;

@Service
public class InvestmentScenarioService {

    private static final List<AssetSeed> ASSETS = List.of(
        new AssetSeed("GREEN", "그린모빌리티", "친환경 이동수단을 만드는 가상 기업", 1_000L),
        new AssetSeed("CLOUD", "클라우드교육", "학교용 디지털 학습 도구를 만드는 가상 기업", 1_250L),
        new AssetSeed("FOOD", "마을푸드", "지역 농산물 유통을 돕는 가상 기업", 800L),
        new AssetSeed("ENERGY", "새빛에너지", "재생 에너지 설비를 만드는 가상 기업", 1_500L)
    );
    private static final List<Integer> GENERATED_LONG_RATES = List.of(
        0, 180, -90, 240, -160, 110, 80, -220, 300, -70,
        140, -130, 210, 60, -180, 250, -40, 120, -100, 190,
        -150, 90, 170, -60, 130, -210, 280, -80, 100, 160
    );
    private static final List<Integer> GENERATED_SHORT_RATES = List.of(0, 650);

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final InvestmentMarketRepository marketRepository;
    private final InvestmentScenarioVersionRepository scenarioRepository;
    private final InvestmentAssetRepository assetRepository;
    private final InvestmentPriceStepRepository priceStepRepository;
    private final HistoricalIndexPathProvider historicalProvider;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public InvestmentScenarioService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomModuleRepository moduleRepository,
        InvestmentMarketRepository marketRepository,
        InvestmentScenarioVersionRepository scenarioRepository,
        InvestmentAssetRepository assetRepository,
        InvestmentPriceStepRepository priceStepRepository,
        HistoricalIndexPathProvider historicalProvider,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.moduleRepository = moduleRepository;
        this.marketRepository = marketRepository;
        this.scenarioRepository = scenarioRepository;
        this.assetRepository = assetRepository;
        this.priceStepRepository = priceStepRepository;
        this.historicalProvider = historicalProvider;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public InvestmentScenarioResult generate(
        String teacherSubject,
        UUID classroomId,
        String requestedMode,
        boolean preferHistorical
    ) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.MANAGE_INVESTMENT_SCENARIO,
            null
        );
        Classroom classroom = classroomRepository.findById(classroomId)
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "학급을 찾을 수 없습니다."));
        ClassroomModule module = moduleRepository.findByClassroomIdAndModuleCode(classroomId, "INVESTMENT")
            .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "MODULE_NOT_ACTIVE", "투자 모듈이 활성화되어 있지 않습니다."));
        InvestmentMode configuredMode = InvestmentMode.valueOf(
            String.valueOf(module.getConfig().getOrDefault("investment_mode", "LONG_TERM"))
        );
        InvestmentMode mode;
        try {
            mode = InvestmentMode.valueOf(requestedMode);
        } catch (RuntimeException exception) {
            throw operation(HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_MODE", "투자 방식이 올바르지 않습니다.");
        }
        if (mode != configuredMode) {
            throw operation(HttpStatus.CONFLICT, "INVESTMENT_MODE_MISMATCH", "모듈 활성화 때 선택한 투자 방식과 다릅니다.");
        }

        Instant now = clock.instant();
        InvestmentMarket market = marketRepository.findForUpdate(classroomId)
            .orElseGet(() -> marketRepository.save(InvestmentMarket.draft(
                uuidGenerator.generate(), classroomId, now
            )));
        if (!InvestmentMarket.DRAFT.equals(market.getStatus())) {
            throw operation(HttpStatus.CONFLICT, "MARKET_NOT_DRAFT", "진행 중인 시장의 시나리오는 변경할 수 없습니다.");
        }
        int stepCount = mode == InvestmentMode.SHORT_TERM
            ? 2
            : (int) Math.min(30, Math.max(2, ChronoUnit.DAYS.between(
                classroom.getActualStartsOn(), classroom.getActualEndsOn()
            ) + 1));
        Optional<HistoricalIndexPath> historical = mode == InvestmentMode.LONG_TERM && preferHistorical
            ? historicalProvider.load(stepCount)
            : Optional.empty();
        InvestmentScenarioSource source = historical.isPresent()
            ? InvestmentScenarioSource.HISTORICAL_INDEX
            : InvestmentScenarioSource.AI_GENERATED;
        HistoricalIndexReference reference = historical.map(HistoricalIndexPath::reference).orElse(null);
        List<Integer> rates = historical.map(HistoricalIndexPath::changeRatesBps)
            .orElseGet(() -> generatedRates(mode, stepCount));
        int nextVersion = scenarioRepository
            .findTopByClassroomIdAndInvestmentMarketIdOrderByVersionDesc(classroomId, market.getId())
            .map(InvestmentScenarioVersion::getVersion)
            .orElse(0) + 1;
        InvestmentScenarioVersion scenario = scenarioRepository.save(InvestmentScenarioVersion.draft(
            uuidGenerator.generate(), classroomId, market.getId(), nextVersion,
            mode == InvestmentMode.SHORT_TERM ? "AI 단기 투자 시나리오" : "AI 장기 투자 시나리오",
            mode, source, reference, now
        ));
        List<InvestmentScenarioResult.Asset> createdAssets = new ArrayList<>();
        for (int index = 0; index < ASSETS.size(); index++) {
            AssetSeed seed = ASSETS.get(index);
            InvestmentAsset asset = assetRepository.save(InvestmentAsset.active(
                uuidGenerator.generate(), classroomId, scenario.getId(), seed.symbol(), seed.name(),
                seed.profile(), index + 1, now
            ));
            long price = seed.initialPrice();
            for (int step = 1; step <= stepCount; step++) {
                int rate = rates.get(step - 1);
                if (step > 1) {
                    price = nextPrice(price, rate);
                }
                priceStepRepository.save(InvestmentPriceStep.hidden(
                    uuidGenerator.generate(), classroomId, asset.getId(), step, price, rate, now
                ));
            }
            createdAssets.add(new InvestmentScenarioResult.Asset(
                asset.getId(), asset.getSymbol(), asset.getName(), seed.initialPrice()
            ));
        }
        market.configure(scenario.getId(), now);
        marketRepository.save(market);
        marketRepository.flush();
        return new InvestmentScenarioResult(
            market.getId(), scenario.getId(), market.getStatus(), mode.name(), source.name(),
            mode.settlementRule(), stepCount, market.getStateVersion(), createdAssets
        );
    }

    @Transactional
    public InvestmentScenarioResult start(String teacherSubject, UUID classroomId, long expectedStateVersion) {
        authorizationService.authorize(
            teacherSubject, classroomId, ClassroomPermission.CONTROL_INVESTMENT_MARKET, null
        );
        InvestmentMarket market = marketRepository.findForUpdate(classroomId)
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "투자 시장을 찾을 수 없습니다."));
        if (market.getStateVersion() != expectedStateVersion) {
            throw operation(HttpStatus.CONFLICT, "STATE_VERSION_CONFLICT", "투자 시장 상태가 변경되었습니다.");
        }
        InvestmentScenarioVersion scenario = scenarioRepository
            .findByClassroomIdAndId(classroomId, market.getActiveScenarioVersionId())
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "INVALID_PRICE_STEP", "투자 시나리오가 없습니다."));
        List<InvestmentAsset> assets = assetRepository
            .findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
                classroomId, scenario.getId(), InvestmentAsset.ACTIVE
            );
        Instant now = clock.instant();
        for (InvestmentAsset asset : assets) {
            InvestmentPriceStep first = priceStepRepository
                .findByClassroomIdAndInvestmentAssetIdAndStepNumber(classroomId, asset.getId(), 1)
                .orElseThrow(() -> operation(HttpStatus.CONFLICT, "INVALID_PRICE_STEP", "첫 가격 단계가 없습니다."));
            first.reveal(now);
            priceStepRepository.save(first);
        }
        scenario.publish(now);
        scenarioRepository.save(scenario);
        market.start(now);
        marketRepository.save(market);
        marketRepository.flush();
        return new InvestmentScenarioResult(
            market.getId(), scenario.getId(), market.getStatus(), scenario.getInvestmentMode().name(),
            scenario.getScenarioSource().name(), scenario.getInvestmentMode().settlementRule(),
            countSteps(classroomId, assets.get(0).getId()), market.getStateVersion(),
            assets.stream().map(asset -> new InvestmentScenarioResult.Asset(
                asset.getId(), asset.getSymbol(), asset.getName(), firstPrice(classroomId, asset.getId())
            )).toList()
        );
    }

    private int countSteps(UUID classroomId, UUID assetId) {
        return priceStepRepository.findAllByClassroomIdAndInvestmentAssetIdOrderByStepNumber(
            classroomId, assetId
        ).size();
    }

    private long firstPrice(UUID classroomId, UUID assetId) {
        return priceStepRepository.findByClassroomIdAndInvestmentAssetIdAndStepNumber(
            classroomId, assetId, 1
        ).orElseThrow().getPrice();
    }

    private static List<Integer> generatedRates(InvestmentMode mode, int count) {
        List<Integer> seed = mode == InvestmentMode.SHORT_TERM
            ? GENERATED_SHORT_RATES : GENERATED_LONG_RATES;
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(seed.get(index % seed.size()));
        }
        result.set(0, 0);
        return List.copyOf(result);
    }

    private static long nextPrice(long current, int rateBps) {
        return BigDecimal.valueOf(current)
            .multiply(BigDecimal.valueOf(10_000L + rateBps))
            .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_UP)
            .max(BigDecimal.ONE)
            .longValueExact();
    }

    private static ClassroomOperationException operation(HttpStatus status, String code, String message) {
        return new ClassroomOperationException(status, code, message);
    }

    private record AssetSeed(String symbol, String name, String profile, long initialPrice) {
    }
}
