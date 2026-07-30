package com.b201.ecovillage.trade.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.trade.application.CounterpartTradeDecisionRequest;
import com.b201.ecovillage.trade.application.CreateTradeCommand;
import com.b201.ecovillage.trade.application.ProposerTradeCommandRequest;
import com.b201.ecovillage.trade.application.TradeCommandResult;
import com.b201.ecovillage.trade.application.TradeCommandService;

@RestController
@RequestMapping("/classrooms/{classroomId}/trades")
public class TradeController {

    private final TradeCommandService tradeService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public TradeController(
        TradeCommandService tradeService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.tradeService = tradeService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TradeCommandResponse>> create(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody CreateTradeRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        TradeCommandResult result = tradeService.create(
            new CreateTradeCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.counterpartStudentId(),
                request.offeredAmount(),
                request.requestedAmount(),
                request.offeredItemIds(),
                request.requestedItemIds(),
                request.description()
            )
        );
        return response(
            HttpStatus.CREATED,
            result,
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/{tradeId}/proposer-commands")
    public ResponseEntity<ApiResponse<TradeCommandResponse>> commandByProposer(
        @PathVariable UUID classroomId,
        @PathVariable UUID tradeId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody ProposerTradeCommandBody request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        TradeCommandResult result = tradeService.commandByProposer(
            new ProposerTradeCommandRequest(
                subject(principal),
                classroomId,
                tradeId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.command()
            )
        );
        return response(HttpStatus.OK, result, requestId, resolvedCorrelationId);
    }

    @PostMapping("/{tradeId}/counterpart-decisions")
    public ResponseEntity<ApiResponse<TradeCommandResponse>> decideByCounterpart(
        @PathVariable UUID classroomId,
        @PathVariable UUID tradeId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody CounterpartTradeDecisionBody request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        TradeCommandResult result = tradeService.decideByCounterpart(
            new CounterpartTradeDecisionRequest(
                subject(principal),
                classroomId,
                tradeId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.decision(),
                request.reason()
            )
        );
        return response(HttpStatus.OK, result, requestId, resolvedCorrelationId);
    }

    private ResponseEntity<ApiResponse<TradeCommandResponse>> response(
        HttpStatus status,
        TradeCommandResult result,
        UUID requestId,
        UUID correlationId
    ) {
        TradeCommandResponse data = new TradeCommandResponse(
            result.tradeId(),
            result.status(),
            result.stateVersion()
        );
        return ResponseEntity.status(status)
            .body(new ApiResponse<>(data, new ApiMeta(requestId, correlationId, clock.instant())));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
