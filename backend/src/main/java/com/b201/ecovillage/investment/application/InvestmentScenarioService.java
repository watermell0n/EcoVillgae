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
import java.util.Random;
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

    /** 교사가 변동폭을 정하지 않았을 때 쓰는 기준 변동폭이다. */
    private static final int DEFAULT_SHORT_VOLATILITY_BPS = 650;
    private static final int DEFAULT_LONG_VOLATILITY_BPS = 300;
    private static final int MIN_VOLATILITY_BPS = 10;
    private static final int MAX_VOLATILITY_BPS = 2_000;
    /** 하루 등락 범위로 허용하는 한계다. 이 밖의 값은 학급 화폐가 하루 만에 무너진다. */
    private static final int MIN_RETURN_BPS = -5_000;
    private static final int MAX_RETURN_BPS = 5_000;

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final InvestmentMarketRepository marketRepository;
    private final InvestmentScenarioVersionRepository scenarioRepository;
    private final InvestmentAssetRepository assetRepository;
    private final InvestmentPriceStepRepository priceStepRepository;
    private final HistoricalIndexPathProvider historicalProvider;
    private final InvestmentCompanyFactory companyFactory;
    private final InvestmentRealtimePublisher realtimePublisher;
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
        InvestmentCompanyFactory companyFactory,
        InvestmentRealtimePublisher realtimePublisher,
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
        this.companyFactory = companyFactory;
        this.realtimePublisher = realtimePublisher;
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
        Map<String, AssetCondition> assetConditions
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
        // 역사 지수를 쓰면 실제 등락을 그대로 살린다. 그 밖에는 종목마다 자기 범위 안에서
        // 매번 새로 뽑으므로, 같은 학급에서 다시 만들어도 흐름이 같지 않다.
        List<Integer> historicalRates = historical.map(HistoricalIndexPath::changeRatesBps).orElse(null);
        Map<String, AssetCondition> confirmed = normalizeConditions(assetConditions);
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
        // 기업 이름은 시나리오마다 AI가 새로 짓는다. 교사가 정한 값은 종목 목록의 자리에
        // 묶여 있으므로 자리와 순서는 그대로 두고 보여 줄 이름만 바뀐다.
        List<InvestmentCompanyFactory.Company> companies = companyFactory.create(classroom, mode);
        for (int index = 0; index < companies.size(); index++) {
            InvestmentCompanyFactory.Company company = companies.get(index);
            InvestmentAssetCatalog.Seed seed = company.seed();
            AssetCondition condition = confirmed.get(seed.symbol());
            ReturnBand band = returnBand(seed, condition, volatilityBps, historicalRates);
            List<Integer> rates = historicalRates == null
                ? drawRates(scenario.getId(), seed.symbol(), stepCount, band)
                : historicalRates;
            // 기호는 자리(종목 목록)의 것을 그대로 쓴다. AI가 지은 기호로 바꾸면 교사가 정한
            // 1주 금액·변동 범위와 AI 추천값이 어느 종목의 것인지 이어지지 않는다. AI가 바꾸는
            // 것은 학생에게 보이는 이름과 소개뿐이다.
            InvestmentAsset asset = assetRepository.save(InvestmentAsset.active(
                uuidGenerator.generate(), classroomId, scenario.getId(), seed.symbol(),
                company.name(), company.profile(), index + 1, band.min(), band.max(), now
            ));
            long startPrice = startPrice(seed, condition);
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
                asset.getId(), asset.getSymbol(), asset.getName(), startPrice,
                band.min(), band.max()
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
        realtimePublisher.publish(classroomId, market, "MARKET_STARTED");
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
        realtimePublisher.publish(
            classroomId, market, pause ? "MARKET_PAUSED" : "MARKET_RESUMED"
        );
        return resultOf(classroomId, market, scenario, assetRepository
            .findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
                classroomId, scenario.getId(), InvestmentAsset.ACTIVE
            ));
    }

    /**
     * 교사가 가상 기업의 이름과 소개 문장을 고친다.
     *
     * <p>AI가 지은 문장에는 초등학생이 읽기 어려운 낱말이 섞일 수 있다. 시나리오를 다시
     * 만들면 가격 흐름까지 바뀌므로, 글만 고칠 수 있는 길을 따로 둔다. 정산이 끝난 시장은
     * 학생이 이미 결과를 본 뒤라 고치지 않는다.
     */
    @Transactional
    public InvestmentAssetTextResult rewriteAssetText(
        String teacherSubject,
        UUID classroomId,
        UUID assetId,
        String name,
        String companyProfile,
        Long startPrice,
        Integer bandBps
    ) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.MANAGE_INVESTMENT_SCENARIO,
            null
        );
        InvestmentAsset asset = assetRepository.findByClassroomIdAndId(classroomId, assetId)
            .filter(InvestmentAsset::isActive)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "종목을 찾을 수 없습니다."
            ));
        try {
            asset.rewrite(name, companyProfile, clock.instant());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "기업 이름은 1~100자, 소개는 1~1000자로 입력해 주세요."
            );
        }
        long resolvedPrice = firstPrice(classroomId, asset.getId());
        if (startPrice != null || bandBps != null) {
            resolvedPrice = repriceAsset(classroomId, asset, startPrice, bandBps);
        }
        assetRepository.save(asset);
        assetRepository.flush();
        return new InvestmentAssetTextResult(
            asset.getId(), asset.getSymbol(), asset.getName(), asset.getFictionalCompanyProfile(),
            resolvedPrice, asset.getMinReturnBps(), asset.getMaxReturnBps()
        );
    }

    /**
     * 1주 금액과 하루 변동 범위를 다시 정하고 가격 흐름을 새로 뽑는다.
     *
     * <p>이미 학생에게 가격을 공개한 뒤에는 바꾸지 않는다. 지난 가격이 그대로인데 앞으로의
     * 흐름만 달라지면 학생이 본 근거와 결과가 어긋난다. 그래서 아직 검토 중(DRAFT)인
     * 시장에서만 허용한다.
     */
    private long repriceAsset(UUID classroomId, InvestmentAsset asset, Long startPrice, Integer bandBps) {
        InvestmentScenarioVersion scenario = scenarioRepository
            .findByClassroomIdAndId(classroomId, asset.getScenarioVersionId())
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "INVALID_PRICE_STEP", "투자 시나리오가 없습니다."));
        InvestmentMarket market = marketRepository.findForUpdate(classroomId, scenario.getInvestmentMode())
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "투자 시장을 찾을 수 없습니다."));
        if (!InvestmentMarket.DRAFT.equals(market.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "MARKET_NOT_DRAFT",
                "이미 시작한 투자에서는 1주 금액과 변동 범위를 바꿀 수 없습니다. 이름과 소개만 고칠 수 있어요."
            );
        }
        if (startPrice != null && startPrice < 1L) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "1주 금액은 1 이상이어야 합니다.");
        }
        if (bandBps != null && (bandBps < 1 || bandBps > MAX_RETURN_BPS)) {
            throw operation(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "변동 범위는 0.01%~50.00% 사이여야 합니다."
            );
        }
        Instant now = clock.instant();
        List<InvestmentPriceStep> steps = priceStepRepository
            .findAllByClassroomIdAndInvestmentAssetIdOrderByStepNumber(classroomId, asset.getId());
        ReturnBand band = bandBps == null
            ? new ReturnBand(asset.getMinReturnBps(), asset.getMaxReturnBps())
            : new ReturnBand(-bandBps, bandBps);
        asset.reband(band.min(), band.max(), now);
        long price = startPrice == null ? steps.get(0).getPrice() : startPrice;
        List<Integer> rates = drawRates(asset.getId(), asset.getSymbol(), steps.size(), band);
        for (int index = 0; index < steps.size(); index++) {
            InvestmentPriceStep step = steps.get(index);
            int rate = rates.get(index);
            if (index > 0) {
                price = nextPrice(price, rate);
            }
            step.reprice(price, rate, now);
            priceStepRepository.save(step);
        }
        return steps.get(0).getPrice();
    }

    /** 교사가 고친 결과다. 화면은 이 값으로 카드를 다시 그린다. */
    public record InvestmentAssetTextResult(
        UUID assetId,
        String symbol,
        String name,
        String companyProfile,
        long startPrice,
        int minReturnBps,
        int maxReturnBps
    ) {
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
                asset.getId(), asset.getSymbol(), asset.getName(),
                firstPrice(classroomId, asset.getId()),
                asset.getMinReturnBps(), asset.getMaxReturnBps()
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

    /** 교사가 보낸 종목별 조건이다. 종목 기호는 대문자로 맞춘다. */
    private static Map<String, AssetCondition> normalizeConditions(
        Map<String, AssetCondition> requested
    ) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<String, AssetCondition> normalized = new LinkedHashMap<>();
        requested.forEach((symbol, condition) -> {
            if (symbol == null || condition == null) {
                return;
            }
            validate(condition);
            normalized.put(symbol.trim().toUpperCase(Locale.ROOT), condition);
        });
        return normalized;
    }

    private static void validate(AssetCondition condition) {
        if (condition.startPrice() != null && condition.startPrice() < 1L) {
            throw operation(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "1주 시작가는 1 이상이어야 합니다."
            );
        }
        Integer min = condition.minReturnBps();
        Integer max = condition.maxReturnBps();
        if ((min == null) != (max == null)) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "하루 변동 범위는 최저와 최고를 함께 보내야 합니다."
            );
        }
        if (min == null) {
            return;
        }
        if (min > max || min < MIN_RETURN_BPS || max > MAX_RETURN_BPS) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "하루 변동 범위는 -50.00%~50.00% 안에서 최저가 최고보다 작아야 합니다."
            );
        }
    }

    /**
     * 종목의 하루 변동 범위다. 교사가 정했으면 그 값을 쓰고, 정하지 않았으면 기준 변동폭에
     * 종목 위험 비중을 곱해 위아래로 같은 폭을 준다. 역사 지수 흐름은 실제 등락이 이미
     * 정해져 있으므로 그 흐름의 최저·최고를 그대로 범위로 삼는다.
     */
    private static ReturnBand returnBand(
        InvestmentAssetCatalog.Seed seed,
        AssetCondition condition,
        int volatilityBps,
        List<Integer> historicalRates
    ) {
        if (historicalRates != null) {
            return new ReturnBand(
                historicalRates.stream().mapToInt(Integer::intValue).min().orElse(0),
                historicalRates.stream().mapToInt(Integer::intValue).max().orElse(0)
            );
        }
        if (condition != null && condition.minReturnBps() != null) {
            return new ReturnBand(condition.minReturnBps(), condition.maxReturnBps());
        }
        long band = InvestmentAssetCatalog.returnBandAt(volatilityBps, seed.riskWeight());
        return new ReturnBand(Math.toIntExact(-band), Math.toIntExact(band));
    }

    /**
     * 종목의 하루 등락을 범위 안에서 새로 뽑는다.
     *
     * <p>고정된 흐름을 쓰면 단기 투자가 늘 같은 결과로 끝나 학생이 판단할 이유가 없어진다.
     * 시나리오 판본 ID와 종목 기호로 씨앗을 만들므로 종목마다 다르게 움직이고, 판본을 새로
     * 만들 때마다 달라지며, 같은 판본 안에서는 몇 번을 계산해도 같은 흐름이 나온다.
     */
    private static List<Integer> drawRates(
        UUID scenarioVersionId,
        String symbol,
        int count,
        ReturnBand band
    ) {
        Random random = new Random(
            scenarioVersionId.getMostSignificantBits()
                ^ scenarioVersionId.getLeastSignificantBits()
                ^ symbol.hashCode()
        );
        List<Integer> rates = new ArrayList<>();
        // 첫날은 시작가를 그대로 공개하므로 등락이 없다.
        rates.add(0);
        for (int step = 2; step <= count; step++) {
            rates.add(band.min() + random.nextInt(band.max() - band.min() + 1));
        }
        return List.copyOf(rates);
    }

    private static long startPrice(InvestmentAssetCatalog.Seed seed, AssetCondition condition) {
        if (condition != null && condition.startPrice() != null) {
            return condition.startPrice();
        }
        return InvestmentAssetCatalog.priceAt(
            InvestmentAssetCatalog.DEFAULT_BASE_PRICE, seed.priceWeight()
        );
    }

    /** 교사가 종목마다 정한 값이다. 보내지 않은 항목은 null로 두고 서버 기본값을 쓴다. */
    public record AssetCondition(Long startPrice, Integer minReturnBps, Integer maxReturnBps) {
    }

    private record ReturnBand(int min, int max) {
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
