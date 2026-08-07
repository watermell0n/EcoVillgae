package com.b201.ecovillage.realtime.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.realtime.application.StudentPresenceService;
import com.b201.ecovillage.realtime.application.StudentPresenceService.StudentPresenceSnapshot;
import com.b201.ecovillage.realtime.infrastructure.RealtimeEventBroadcaster;

@RestController
public class StudentPresenceController {

    private final ClassroomAuthorizationService authorizationService;
    private final StudentPresenceService presenceService;
    private final RealtimeEventBroadcaster broadcaster;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentPresenceController(
        ClassroomAuthorizationService authorizationService,
        StudentPresenceService presenceService,
        RealtimeEventBroadcaster broadcaster,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.presenceService = presenceService;
        this.broadcaster = broadcaster;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/classrooms/{classroomId}/students/me/presence")
    public ApiResponse<StudentPresenceResponse> update(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody StudentPresenceRequest request,
        Principal principal
    ) {
        String subject = principal == null ? null : principal.getName();
        UUID studentId = studentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_MODULES_AND_POLICIES,
            studentId
        );
        StudentPresenceSnapshot presence = presenceService.update(
            classroomId,
            studentId,
            "http-fallback-" + studentId,
            request.status(),
            request.currentScreen(),
            clock.instant()
        );
        broadcaster.broadcastPresence(presence);
        UUID requestId = uuidGenerator.generate();
        UUID correlation = correlationId == null ? uuidGenerator.generate() : correlationId;
        return new ApiResponse<>(
            new StudentPresenceResponse(
                presence.studentId(),
                presence.status(),
                presence.currentScreen(),
                presence.lastSeenAt()
            ),
            new ApiMeta(requestId, correlation, clock.instant())
        );
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid student session is required."
            );
        }
    }
}
