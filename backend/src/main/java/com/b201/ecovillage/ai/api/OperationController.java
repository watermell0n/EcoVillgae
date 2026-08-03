package com.b201.ecovillage.ai.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.ai.application.AsyncOperationQueryService;
import com.b201.ecovillage.ai.application.AsyncOperationView;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
public class OperationController {

    private final AsyncOperationQueryService queryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public OperationController(
        AsyncOperationQueryService queryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.queryService = queryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/operations/{operationId}")
    public ApiResponse<AsyncOperationView> find(
        @PathVariable UUID operationId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        return new ApiResponse<>(
            queryService.find(subject(principal), operationId),
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        );
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
