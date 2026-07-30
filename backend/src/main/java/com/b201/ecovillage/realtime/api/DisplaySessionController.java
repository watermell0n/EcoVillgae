package com.b201.ecovillage.realtime.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.realtime.application.DisplaySessionCommand;
import com.b201.ecovillage.realtime.application.DisplaySessionResult;
import com.b201.ecovillage.realtime.application.DisplaySessionService;
import com.b201.ecovillage.realtime.application.PublicDisplayView;

@RestController
public class DisplaySessionController {

    private final DisplaySessionService service;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public DisplaySessionController(
        DisplaySessionService service,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.service = service;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping(
        "/classrooms/{classroomId}/guided-activities/{activityId}/display-session-commands"
    )
    public ApiResponse<DisplaySessionResponse> command(
        @PathVariable UUID classroomId,
        @PathVariable UUID activityId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody DisplaySessionCommandRequest request,
        Principal principal
    ) {
        UUID correlation = resolve(correlationId);
        DisplaySessionResult result = service.execute(
            new DisplaySessionCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                activityId,
                requestId,
                correlation,
                request.expectedStateVersion(),
                request.command(),
                request.displaySessionId()
            )
        );
        return new ApiResponse<>(
            new DisplaySessionResponse(
                result.displaySessionId(),
                result.displayToken(),
                result.expiresAt(),
                result.status()
            ),
            meta(requestId, correlation)
        );
    }

    @GetMapping("/public/display-sessions/{displaySessionId}/bootstrap")
    public ApiResponse<PublicDisplayResponse> bootstrap(
        @PathVariable UUID displaySessionId,
        @RequestHeader("Authorization") String authorization,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID correlation = resolve(correlationId);
        PublicDisplayView view = service.bootstrap(
            displaySessionId,
            bearerToken(authorization)
        );
        PublicDisplayResponse.Timer timer = view.endsAt() == null
            ? null
            : new PublicDisplayResponse.Timer(view.serverTime(), view.endsAt());
        return new ApiResponse<>(
            new PublicDisplayResponse(
                view.activityId(),
                view.scene(),
                timer,
                view.publicData()
            ),
            meta(requestId, correlation)
        );
    }

    private ApiMeta meta(UUID requestId, UUID correlationId) {
        return new ApiMeta(requestId, correlationId, clock.instant());
    }

    private UUID resolve(UUID correlationId) {
        return correlationId == null ? uuidGenerator.generate() : correlationId;
    }

    private static String bearerToken(String authorization) {
        if (
            authorization == null
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
        ) {
            return null;
        }
        return authorization.substring(7).trim();
    }
}
