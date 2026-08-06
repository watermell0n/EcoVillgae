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
import com.b201.ecovillage.store.application.StorePriceInflationCommand;
import com.b201.ecovillage.store.application.StorePriceInflationResult;
import com.b201.ecovillage.store.application.StorePriceInflationService;

/**
 * 상점 상품 가격을 한 번의 요청으로 일괄 인상한다.
 *
 * <p>교사 화면이 상품마다 세 번씩 명령을 보내던 것을 대체한다. 상품이 늘어도 요청은 한 번이다.
 */
@RestController
@RequestMapping("/classrooms/{classroomId}/store/price-inflation-commands")
public class StorePriceInflationController {

    private final StorePriceInflationService inflationService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StorePriceInflationController(
        StorePriceInflationService inflationService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.inflationService = inflationService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StorePriceInflationResponse>> apply(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody StorePriceInflationRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        StorePriceInflationResult result = inflationService.apply(
            new StorePriceInflationCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.expectedPolicyVersion(),
                request.rateBps()
            )
        );
        return ResponseEntity.ok(new ApiResponse<>(
            new StorePriceInflationResponse(
                result.appliedCount(),
                result.clampedCount(),
                result.unchangedCount(),
                result.stateVersion()
            ),
            new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
        ));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
