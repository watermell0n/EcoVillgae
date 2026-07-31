package com.b201.ecovillage.classroom.api;

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

import com.b201.ecovillage.classroom.application.SalaryPayoutCommand;
import com.b201.ecovillage.classroom.application.SalaryPayoutResult;
import com.b201.ecovillage.classroom.application.SalaryPayoutService;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
@RequestMapping("/classrooms/{classroomId}/payouts")
public class PayoutController {

    private final SalaryPayoutService payoutService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public PayoutController(
        SalaryPayoutService payoutService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.payoutService = payoutService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PayoutResponse>> payout(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody PayoutRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        SalaryPayoutResult result = payoutService.pay(
            new SalaryPayoutCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.payoutType(),
                request.studentIds(),
                request.periodKey(),
                request.amount(),
                request.policyVersion(),
                request.reason(),
                request.expectedStateVersion(),
                request.clientOccurredAt()
            )
        );
        PayoutResponse data = new PayoutResponse(
            result.transactionGroupId(),
            result.status(),
            result.stateVersion()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                new ApiResponse<>(
                    data,
                    new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
                )
            );
    }
}
