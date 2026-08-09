package com.b201.ecovillage.investment.api;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.investment.application.InvestmentBuyCommand;
import com.b201.ecovillage.investment.application.InvestmentBuyResult;
import com.b201.ecovillage.investment.application.InvestmentBuyService;
import com.b201.ecovillage.investment.application.InvestmentDayResult;
import com.b201.ecovillage.investment.application.InvestmentDayService;
import com.b201.ecovillage.investment.application.InvestmentMarketQueryService;
import com.b201.ecovillage.investment.application.InvestmentMarketView;
import com.b201.ecovillage.investment.application.InvestmentScenarioResult;
import com.b201.ecovillage.investment.application.InvestmentScenarioService;

@RestController
@RequestMapping("/classrooms/{classroomId}")
public class InvestmentController {

    private final InvestmentScenarioService scenarioService;
    private final InvestmentMarketQueryService queryService;
    private final InvestmentBuyService buyService;
    private final InvestmentDayService dayService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public InvestmentController(
        InvestmentScenarioService scenarioService,
        InvestmentMarketQueryService queryService,
        InvestmentBuyService buyService,
        InvestmentDayService dayService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.scenarioService = scenarioService;
        this.queryService = queryService;
        this.buyService = buyService;
        this.dayService = dayService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PutMapping("/investment-market/scenario")
    public ApiResponse<InvestmentScenarioResult> generateScenario(
        @PathVariable UUID classroomId,
        @Valid @RequestBody InvestmentScenarioRequest request,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        InvestmentScenarioResult result = scenarioService.generate(
            subject(principal), classroomId, request.investmentMode(), request.preferHistoricalIndex(),
            request.dailyVolatilityBps(), request.conditionsBySymbol()
        );
        return new ApiResponse<>(result, new ApiMeta(requestId, resolvedCorrelationId, clock.instant()));
    }

    /**
     * 가상 기업의 이름과 소개 문장만 고친다. 가격 흐름은 그대로 두므로 시나리오를 다시
     * 만들지 않고도 어려운 낱말을 학생 눈높이로 바꿀 수 있다.
     */
    @PutMapping("/investment-market/assets/{assetId}")
    public ApiResponse<InvestmentScenarioService.InvestmentAssetTextResult> rewriteAssetText(
        @PathVariable UUID classroomId,
        @PathVariable UUID assetId,
        @Valid @RequestBody InvestmentAssetTextRequest request,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        InvestmentScenarioService.InvestmentAssetTextResult result = scenarioService.rewriteAssetText(
            subject(principal), classroomId, assetId, request.name(), request.companyProfile(),
            request.startPrice(), request.bandBps()
        );
        return new ApiResponse<>(result, new ApiMeta(requestId, resolvedCorrelationId, clock.instant()));
    }

    @PostMapping("/investment-market/commands")
    public ApiResponse<InvestmentScenarioResult> commandMarket(
        @PathVariable UUID classroomId,
        @Valid @RequestBody InvestmentMarketCommandRequest request,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        InvestmentScenarioResult result = switch (request.command()) {
            case "START" -> scenarioService.start(
                subject(principal), classroomId, request.investmentMode(), request.expectedStateVersion()
            );
            // 학생 이용 잠금과 재개는 방식마다 따로 걸리므로 모듈이 아니라 시장 상태로 다룬다.
            case "PAUSE" -> scenarioService.changeStudentAccess(
                subject(principal), classroomId, request.investmentMode(), true,
                request.expectedStateVersion()
            );
            case "RESUME" -> scenarioService.changeStudentAccess(
                subject(principal), classroomId, request.investmentMode(), false,
                request.expectedStateVersion()
            );
            default -> throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "START, PAUSE, RESUME 명령만 지원합니다."
            );
        };
        return new ApiResponse<>(result, new ApiMeta(requestId, resolvedCorrelationId, clock.instant()));
    }

    @GetMapping("/investment-market")
    public ApiResponse<InvestmentMarketView> findMarket(
        @PathVariable UUID classroomId,
        @RequestParam(name = "investment_mode") String investmentMode,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        return new ApiResponse<>(
            queryService.find(subject(principal), classroomId, investmentMode),
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        );
    }

    /**
     * 투자 시장을 다음 날로 넘긴다. 숨겨 둔 다음 가격 단계를 공개하고, 정산할 시점이면
     * 학생 보유량을 현금으로 되돌린 뒤 시장을 닫는다.
     *
     * <p>정산 결과(지급 인원·금액)를 함께 돌려주므로 응답 모양이 START·PAUSE·RESUME과 달라
     * 명령 엔드포인트를 따로 둔다.
     */
    @PostMapping("/investment-market/day-commands")
    public ApiResponse<InvestmentDayResult> advanceDay(
        @PathVariable UUID classroomId,
        @Valid @RequestBody InvestmentDayCommandRequest request,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        InvestmentDayResult result = dayService.advanceDay(
            subject(principal), classroomId, request.investmentMode(), request.expectedStateVersion()
        );
        return new ApiResponse<>(result, new ApiMeta(requestId, resolvedCorrelationId, clock.instant()));
    }

    /**
     * 학급이 운영하는 단기·장기 시장을 한 번에 돌려준다. 학생 화면은 두 시장을 함께 보여 준다.
     */
    @GetMapping("/investment-markets")
    public ApiResponse<InvestmentMarketListView> findMarkets(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        return new ApiResponse<>(
            new InvestmentMarketListView(queryService.findAll(subject(principal), classroomId)),
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        );
    }

    @PostMapping("/investment-orders")
    public ResponseEntity<ApiResponse<InvestmentBuyResult>> order(
        @PathVariable UUID classroomId,
        @Valid @RequestBody InvestmentOrderRequest request,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        if (!List.of("BUY", "SELL").contains(request.side())) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "BUY 또는 SELL 주문만 지원합니다."
            );
        }
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        InvestmentBuyCommand command = new InvestmentBuyCommand(
            subject(principal), classroomId, requestId, resolvedCorrelationId, request.assetId(),
            request.quantity(), request.expectedPriceVersion()
        );
        InvestmentBuyResult result = "BUY".equals(request.side())
            ? buyService.buy(command)
            : buyService.sell(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new ApiResponse<>(result, new ApiMeta(requestId, resolvedCorrelationId, clock.instant()))
        );
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}

/**
 * 투자 시나리오 생성 요청이다. dailyVolatilityBps와 assetConditions는 교사가
 * "운영 관리 &gt; 투자"에서 정한 값이며, 없으면 서버 기본값으로 만든다.
 */
/**
 * 교사가 고친 종목 내용이다.
 *
 * <p>이름과 소개는 언제든 고칠 수 있다. 1주 금액과 변동 범위는 보내면 가격 흐름을 다시
 * 뽑으므로, 아직 시작하지 않은 시장에서만 받는다. 보내지 않으면 지금 값을 그대로 둔다.
 */
record InvestmentAssetTextRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 1000) String companyProfile,
    @Min(1) Long startPrice,
    @Min(1) Integer bandBps
) {
}

record InvestmentScenarioRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String investmentMode,
    boolean preferHistoricalIndex,
    Integer dailyVolatilityBps,
    @Valid List<AssetConditionRequest> assetConditions
) {
    InvestmentScenarioRequest {
        assetConditions = assetConditions == null ? List.of() : List.copyOf(assetConditions);
    }

    Map<String, InvestmentScenarioService.AssetCondition> conditionsBySymbol() {
        Map<String, InvestmentScenarioService.AssetCondition> conditions = new LinkedHashMap<>();
        assetConditions.forEach(condition -> conditions.put(
            condition.symbol(),
            new InvestmentScenarioService.AssetCondition(
                condition.startPrice(), condition.minReturnBps(), condition.maxReturnBps()
            )
        ));
        return conditions;
    }
}

record AssetConditionRequest(
    @NotBlank String symbol,
    @Min(1) Long startPrice,
    Integer minReturnBps,
    Integer maxReturnBps
) {
}

record InvestmentMarketCommandRequest(
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String command,
    @NotBlank String investmentMode,
    @Min(0) long expectedStateVersion
) {
}

record InvestmentMarketListView(java.util.List<InvestmentMarketView> markets) {
}

record InvestmentDayCommandRequest(
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String investmentMode,
    @Min(0) long expectedStateVersion
) {
}

record InvestmentOrderRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String side,
    @NotNull UUID assetId,
    @Min(1) @Max(100) int quantity,
    @Min(1) int expectedPriceVersion
) {
}
