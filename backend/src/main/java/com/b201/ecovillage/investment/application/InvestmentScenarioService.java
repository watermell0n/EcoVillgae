package com.b201.ecovillage.investment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.b201.ecovillage.investment.domain.InvestmentAssetCatalog;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentMode;
import com.b201.ecovillage.investment.domain.InvestmentModeConfig;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.domain.InvestmentScenarioSource;
import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentPriceStepRepository;
import com.b201.ecovillage.investment.repository.InvestmentScenarioVersionRepository;

@Service
public class InvestmentScenarioService {

    /**
     * AI 생성 흐름의 가격 모양이다. 하루 최대 변동폭을 1,000으로 본 천분율이며, 교사가 정한
     * 변동폭을 곱해 실제 등락률(bps)로 바꾼다. 모양만 고정하고 크기는 교사가 정하므로,
     * 같은 학급이라도 변동폭을 낮추면 흐름은 같고 폭만 완만해진다.
     */
    private static final List<Integer> LONG_SHAPE_PERMILLE = List.of(
        0, 600, -300, 800, -533, 367, 267, -733, 1_000, -233,
        467, -433, 700, 200, -600, 833, -133, 400, -333, 633,
        -500, 300, 567, -200, 433, -700, 933, -267, 333, 533
    );
    /** 단기는 하루만 움직인다. 첫날은 시작가 그대로이고 다음 날 변동폭만큼 움직인다. */
    private static final List<Integer> SHORT_SHAPE_PERMILLE = List.of(0, 1_000);
    /** 교사가 변동폭을 정하지 않았을 때 쓰는 값이다. 기존 시나리오와 같은 흐름이 나온다. */
    private static final int DEFAULT_SHORT_VOLATILITY_BPS = 650;
    private static final int DEFAULT_LONG_VOLATILITY_BPS = 300;
    private static final int MIN_VOLATILITY_BPS = 10;
    private static final int MAX_VOLATILITY_BPS = 2_000;

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

    /**
     * 투자 시나리오를 만든다.
     *
     * <p>하루 최대 변동폭과 종목별 1주 시작가는 교사가 "운영 관리 &gt; 투자"에서 정해 보낸다.
     * 보내지 않으면 기본값으로 만든다. 장기 역사 지수를 쓰는 경우에는 실제 흐름을 그대로
     * 살려야 하므로 변동폭을 적용하지 않는다.
     */
    @Transactional
    public InvestmentScenarioResult generate(
        String teacherSubject,
        UUID classroomId,
        String requestedMode,
        boolean preferHistorical,
        Integer dailyVolatilityBps,
        Map<String, Long> assetStartPrices
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
        InvestmentMode mode = InvestmentModeConfig.parse(requestedMode)
            .orElseThrow(() -> operation(
                HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_MODE", "투자 방식이 올바르지 않습니다."
            ));
        // 단기와 장기는 함께 운영할 수 있다. 다만 교사가 고르지 않은 방식은 만들 수 없다.
        if (!InvestmentModeConfig.enabledModes(module.getConfig()).contains(mode)) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVESTMENT_MODE_NOT_ENABLED",
                mode == InvestmentMode.SHORT_TERM
                    ? "단기 투자를 사용하도록 먼저 활성화해 주세요."
                    : "장기 투자를 사용하도록 먼저 활성화해 주세요."
            );
        }

        Instant now = clock.instant();
        InvestmentMarket market = marketRepository.findForUpdate(classroomId, mode)
            .orElseGet(() -> marketRepository.save(InvestmentMarket.draft(
                uuidGenerator.generate(), classroomId, mode, now
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
        int volatilityBps = volatilityBps(mode, dailyVolatilityBps);
        List<Integer> rates = historical.map(HistoricalIndexPath::changeRatesBps)
            .orElseGet(() -> generatedRates(mode, stepCount, volatilityBps));
        Map<String, Long> confirmedPrices = normalizePrices(assetStartPrices);
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
        List<InvestmentAssetCatalog.Seed> seeds = InvestmentAssetCatalog.seeds();
        for (int index = 0; index < seeds.size(); index++) {
            InvestmentAssetCatalog.Seed seed = seeds.get(index);
            InvestmentAsset asset = assetRepository.save(InvestmentAsset.active(
                uuidGenerator.generate(), classroomId, scenario.getId(), seed.symbol(), seed.name(),
                seed.profile(), index + 1, now
            ));
            long startPrice = startPrice(seed, confirmedPrices);
            long price = startPrice;
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
                asset.getId(), asset.getSymbol(), asset.getName(), startPrice
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
    public InvestmentScenarioResult start(
        String teacherSubject,
        UUID classroomId,
        String requestedMode,
        long expectedStateVersion
    ) {
        authorizationService.authorize(
            teacherSubject, classroomId, ClassroomPermission.CONTROL_INVESTMENT_MARKET, null
        );
        InvestmentMode mode = InvestmentModeConfig.parse(requestedMode)
            .orElseThrow(() -> operation(
                HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_MODE", "투자 방식이 올바르지 않습니다."
            ));
        InvestmentMarket market = marketRepository.findForUpdate(classroomId, mode)
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
        return resultOf(classroomId, market, scenario, assets);
    }

    /**
     * 이 방식의 투자를 학생이 쓰지 못하게 잠그거나 다시 연다. 학급 설정에서 단기와 장기를
     * 따로 잠글 수 있어야 하므로 모듈 전체가 아니라 방식별 시장 상태로 다룬다.
     */
    @Transactional
    public InvestmentScenarioResult changeStudentAccess(
        String teacherSubject,
        UUID classroomId,
        String requestedMode,
        boolean pause,
        long expectedStateVersion
    ) {
        authorizationService.authorize(
            teacherSubject, classroomId, ClassroomPermission.CONTROL_INVESTMENT_MARKET, null
        );
        InvestmentMode mode = InvestmentModeConfig.parse(requestedMode)
            .orElseThrow(() -> operation(
                HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_MODE", "투자 방식이 올바르지 않습니다."
            ));
        InvestmentMarket market = marketRepository.findForUpdate(classroomId, mode)
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "투자 시장을 찾을 수 없습니다."));
        if (market.getStateVersion() != expectedStateVersion) {
            throw operation(HttpStatus.CONFLICT, "STATE_VERSION_CONFLICT", "투자 시장 상태가 변경되었습니다.");
        }
        String expected = pause ? InvestmentMarket.OPEN : InvestmentMarket.PAUSED;
        if (!expected.equals(market.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                pause ? "열려 있는 투자만 잠글 수 있습니다." : "잠긴 투자만 다시 열 수 있습니다."
            );
        }
        InvestmentScenarioVersion scenario = scenarioRepository
            .findByClassroomIdAndId(classroomId, market.getActiveScenarioVersionId())
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "INVALID_PRICE_STEP", "투자 시나리오가 없습니다."));
        Instant now = clock.instant();
        if (pause) {
            market.pauseForStudents(now);
        } else {
            market.resumeForStudents(now);
        }
        marketRepository.save(market);
        marketRepository.flush();
        return resultOf(classroomId, market, scenario, assetRepository
            .findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
                classroomId, scenario.getId(), InvestmentAsset.ACTIVE
            ));
    }

    private InvestmentScenarioResult resultOf(
        UUID classroomId,
        InvestmentMarket market,
        InvestmentScenarioVersion scenario,
        List<InvestmentAsset> assets
    ) {
        return new InvestmentScenarioResult(
            market.getId(), scenario.getId(), market.getStatus(), market.getInvestmentMode().name(),
            scenario.getScenarioSource().name(), market.getInvestmentMode().settlementRule(),
            assets.isEmpty() ? 0 : countSteps(classroomId, assets.get(0).getId()),
            market.getStateVersion(),
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

    /**
     * 교사가 정한 하루 최대 변동폭이다. 값이 없거나 허용 범위를 벗어나면 기본값으로 되돌린다.
     * 여기서 막지 않으면 0%짜리 시나리오나 하루에 몇 배가 되는 시나리오가 만들어진다.
     */
    private static int volatilityBps(InvestmentMode mode, Integer requested) {
        int fallback = mode == InvestmentMode.SHORT_TERM
            ? DEFAULT_SHORT_VOLATILITY_BPS : DEFAULT_LONG_VOLATILITY_BPS;
        if (requested == null) {
            return fallback;
        }
        if (requested < MIN_VOLATILITY_BPS || requested > MAX_VOLATILITY_BPS) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "하루 최대 변동폭은 0.10%~20.00% 사이여야 합니다."
            );
        }
        return requested;
    }

    /** 교사가 보낸 1주 시작가다. 종목 기호는 대문자로 맞춘다. */
    private static Map<String, Long> normalizePrices(Map<String, Long> requested) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> normalized = new LinkedHashMap<>();
        requested.forEach((symbol, price) -> {
            if (symbol == null || price == null) {
                return;
            }
            if (price < 1L) {
                throw operation(
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "1주 시작가는 1 이상이어야 합니다."
                );
            }
            normalized.put(symbol.trim().toUpperCase(Locale.ROOT), price);
        });
        return normalized;
    }

    private static long startPrice(
        InvestmentAssetCatalog.Seed seed,
        Map<String, Long> confirmedPrices
    ) {
        Long confirmed = confirmedPrices.get(seed.symbol());
        return confirmed == null
            ? InvestmentAssetCatalog.priceAt(InvestmentAssetCatalog.DEFAULT_BASE_PRICE, seed.priceWeight())
            : confirmed;
    }

    private static List<Integer> generatedRates(InvestmentMode mode, int count, int volatilityBps) {
        List<Integer> shape = mode == InvestmentMode.SHORT_TERM
            ? SHORT_SHAPE_PERMILLE : LONG_SHAPE_PERMILLE;
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(scaleRate(shape.get(index % shape.size()), volatilityBps));
        }
        result.set(0, 0);
        return List.copyOf(result);
    }

    private static int scaleRate(int shapePermille, int volatilityBps) {
        return BigDecimal.valueOf((long) shapePermille * volatilityBps)
            .divide(BigDecimal.valueOf(1_000L), 0, RoundingMode.HALF_UP)
            .intValueExact();
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
}
