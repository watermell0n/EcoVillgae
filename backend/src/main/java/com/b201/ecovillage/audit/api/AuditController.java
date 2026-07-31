package com.b201.ecovillage.audit.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
public class AuditController {

    private final AuditTrailService service;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AuditController(
        AuditTrailService service,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.service = service;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/classrooms/{classroomId}/audit-events")
    public ApiResponse<AuditEventsResponse> findAll(
        @PathVariable UUID classroomId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID correlation = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        AuditEventsResponse response = new AuditEventsResponse(
            service.findAll(
                principal == null ? null : principal.getName(),
                classroomId,
                limit
            ).stream().map(event -> new AuditEventResponse(
                event.auditEventId(),
                event.actorType(),
                event.actorId(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.requestId(),
                event.correlationId(),
                event.beforeSummary(),
                event.afterSummary(),
                event.occurredAt()
            )).toList()
        );
        return new ApiResponse<>(
            response,
            new ApiMeta(requestId, correlation, clock.instant())
        );
    }
}
