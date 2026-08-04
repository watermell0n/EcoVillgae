package com.b201.ecovillage.investment.api;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.investment.application.InvestmentBuyCommand;
import com.b201.ecovillage.investment.application.InvestmentBuyResult;
import com.b201.ecovillage.investment.application.InvestmentBuyService;
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
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public InvestmentController(
        InvestmentScenarioService scenarioService,
        InvestmentMarketQueryService queryService,
        InvestmentBuyService buyService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.scenarioService = scenarioService;
        this.queryService = queryService;
        this.buyService = buyService;
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
            subject(principal), classroomId, request.investmentMode(), request.preferHistoricalIndex()
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
        if (!"START".equals(request.command())) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "현재 단계에서는 START 명령만 지원합니다."
            );
        }
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        InvestmentScenarioResult result = scenarioService.start(
            subject(principal), classroomId, request.expectedStateVersion()
        );
        return new ApiResponse<>(result, new ApiMeta(requestId, resolvedCorrelationId, clock.instant()));
    }

    @GetMapping("/investment-market")
    public ApiResponse<InvestmentMarketView> findMarket(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        return new ApiResponse<>(
            queryService.find(subject(principal), classroomId),
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        );
    }

    @PostMapping("/investment-orders")
    public ResponseEntity<ApiResponse<InvestmentBuyResult>> buy(
        @PathVariable UUID classroomId,
        @Valid @RequestBody InvestmentOrderRequest request,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        if (!"BUY".equals(request.side())) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "현재 단계에서는 BUY 주문만 지원합니다."
            );
        }
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        InvestmentBuyResult result = buyService.buy(new InvestmentBuyCommand(
            subject(principal), classroomId, requestId, resolvedCorrelationId, request.assetId(),
            request.quantity(), request.expectedPriceVersion()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new ApiResponse<>(result, new ApiMeta(requestId, resolvedCorrelationId, clock.instant()))
        );
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}

record InvestmentScenarioRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String investmentMode,
    boolean preferHistoricalIndex
) {
}

record InvestmentMarketCommandRequest(
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String command,
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
