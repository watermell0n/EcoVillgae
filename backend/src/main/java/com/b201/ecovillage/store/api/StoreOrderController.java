package com.b201.ecovillage.store.api;

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
import com.b201.ecovillage.store.application.StorePurchaseCommand;
import com.b201.ecovillage.store.application.StorePurchaseResult;
import com.b201.ecovillage.store.application.StorePurchaseService;

@RestController
@RequestMapping("/classrooms/{classroomId}/store/orders")
public class StoreOrderController {

    private final StorePurchaseService purchaseService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StoreOrderController(
        StorePurchaseService purchaseService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.purchaseService = purchaseService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StorePurchaseResponse>> purchase(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody StorePurchaseRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        StorePurchaseResult result = purchaseService.purchase(
            new StorePurchaseCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.productId(),
                request.quantity(),
                request.expectedProductVersion(),
                request.expectedStateVersion()
            )
        );
        StorePurchaseResponse data = new StorePurchaseResponse(
            result.orderId(),
            result.status(),
            result.totalAmount(),
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
