package com.b201.ecovillage.store.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

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
import com.b201.ecovillage.store.application.StoreProductCommand;
import com.b201.ecovillage.store.application.StoreProductCommandResult;
import com.b201.ecovillage.store.application.StoreProductCommandService;

@RestController
@RequestMapping("/classrooms/{classroomId}/store/product-commands")
public class StoreProductCommandController {

    private final StoreProductCommandService commandService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StoreProductCommandController(
        StoreProductCommandService commandService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.commandService = commandService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreProductCommandResponse>> execute(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody StoreProductCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        StoreProductCommandResult result = commandService.execute(
            new StoreProductCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.expectedPolicyVersion(),
                request.command(),
                request.productId(),
                request.name(),
                request.description(),
                request.productKind(),
                request.unitPrice(),
                request.vatRateBps(),
                request.stockQuantity(),
                request.characterSourceId(),
                request.expectedProductVersion()
            )
        );
        return ResponseEntity.ok(new ApiResponse<>(
            new StoreProductCommandResponse(
                result.productId(),
                result.status(),
                result.stateVersion(),
                result.version()
            ),
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        ));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
