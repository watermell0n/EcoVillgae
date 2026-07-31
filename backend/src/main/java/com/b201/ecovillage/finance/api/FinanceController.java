package com.b201.ecovillage.finance.api;

import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.application.DepositAccountQueryService;
import com.b201.ecovillage.finance.application.DepositAccountResult;
import com.b201.ecovillage.finance.application.DepositSubscriptionCommand;
import com.b201.ecovillage.finance.application.DepositSubscriptionService;
import com.b201.ecovillage.finance.application.FinancialProductCommand;
import com.b201.ecovillage.finance.application.FinancialProductCommandService;
import com.b201.ecovillage.finance.application.FinancialResourceResult;

@RestController
@RequestMapping("/classrooms/{classroomId}")
public class FinanceController {

    private final FinancialProductCommandService productService;
    private final DepositSubscriptionService depositService;
    private final DepositAccountQueryService queryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public FinanceController(
        FinancialProductCommandService productService,
        DepositSubscriptionService depositService,
        DepositAccountQueryService queryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.productService = productService;
        this.depositService = depositService;
        this.queryService = queryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/financial-product-commands")
    public ResponseEntity<ApiResponse<FinancialResourceResponse>> commandProduct(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody FinancialProductCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        FinancialResourceResult result = productService.execute(
            new FinancialProductCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.command(),
                request.productId(),
                request.productType(),
                request.name(),
                request.termUnits(),
                request.interestRateBps(),
                request.earlyTerminationRateBps(),
                request.installmentCount(),
                request.installmentAmount(),
                request.minimumAmount(),
                request.maximumAmount(),
                request.expectedProductVersion()
            )
        );
        return response(
            HttpStatus.OK,
            new FinancialResourceResponse(
                result.financialResourceId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/deposit-accounts")
    public ResponseEntity<ApiResponse<FinancialResourceResponse>> subscribeDeposit(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody DepositSubscriptionRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        DepositAccountResult result = depositService.subscribe(
            new DepositSubscriptionCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.productId(),
                request.principal(),
                request.expectedProductVersion()
            )
        );
        return response(
            HttpStatus.CREATED,
            new FinancialResourceResponse(
                result.accountId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @GetMapping("/students/me/deposit-and-savings")
    public ResponseEntity<ApiResponse<DepositAndSavingsResponse>> findMyAccounts(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        List<DepositAccountViewResponse> deposits = queryService
            .findMine(subject(principal), classroomId)
            .stream()
            .map(view -> new DepositAccountViewResponse(
                view.resourceId(),
                view.label(),
                view.status(),
                view.version(),
                view.stateVersion(),
                view.principalAmount(),
                view.maturesAt()
            ))
            .toList();
        return response(
            HttpStatus.OK,
            new DepositAndSavingsResponse(deposits, List.of()),
            requestId,
            resolvedCorrelationId
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> response(
        HttpStatus status,
        T data,
        UUID requestId,
        UUID correlationId
    ) {
        return ResponseEntity.status(status)
            .body(new ApiResponse<>(
                data,
                new ApiMeta(requestId, correlationId, clock.instant())
            ));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
