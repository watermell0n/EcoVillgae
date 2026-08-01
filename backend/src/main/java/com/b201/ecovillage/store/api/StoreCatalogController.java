package com.b201.ecovillage.store.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.store.application.StoreCatalogQueryService;

@RestController
@RequestMapping("/classrooms/{classroomId}/store/catalog")
public class StoreCatalogController {

    private final StoreCatalogQueryService queryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StoreCatalogController(
        StoreCatalogQueryService queryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.queryService = queryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StoreCatalogResponse>> findPublished(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        StoreCatalogResponse data = new StoreCatalogResponse(
            queryService.findPublished(subject(principal), classroomId)
                .stream()
                .map(view -> new StoreCatalogProductResponse(
                    view.resourceId(),
                    view.label(),
                    view.status(),
                    view.version(),
                    view.stateVersion(),
                    view.unitPrice(),
                    view.productKind(),
                    view.characterCode()
                ))
                .toList()
        );
        return ResponseEntity.ok(new ApiResponse<>(
            data,
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        ));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
