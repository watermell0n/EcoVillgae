package com.b201.ecovillage.ai.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.ai.application.AiSettingsCommand;
import com.b201.ecovillage.ai.application.AiSettingsResult;
import com.b201.ecovillage.ai.application.AiSettingsService;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
@RequestMapping("/classrooms/{classroomId}/ai-settings")
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AiSettingsController(
        AiSettingsService aiSettingsService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.aiSettingsService = aiSettingsService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AiSettingsResponse>> updateAiSettings(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody AiSettingsRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        AiSettingsResult result = aiSettingsService.update(new AiSettingsCommand(
            principal == null ? null : principal.getName(),
            classroomId,
            requestId,
            resolvedCorrelationId,
            Boolean.TRUE.equals(request.enabled()),
            request.reason(),
            request.expectedStateVersion()
        ));
        AiSettingsResponse response = new AiSettingsResponse(
            result.aiResourceId(),
            result.status(),
            result.stateVersion()
        );
        return ResponseEntity.ok(
            new ApiResponse<>(response, meta(requestId, resolvedCorrelationId))
        );
    }

    private ApiMeta meta(UUID requestId, UUID correlationId) {
        return new ApiMeta(requestId, correlationId, clock.instant());
    }
}
