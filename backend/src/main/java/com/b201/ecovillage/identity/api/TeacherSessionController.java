package com.b201.ecovillage.identity.api;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.application.TeacherLoginResult;
import com.b201.ecovillage.identity.application.TeacherLoginService;

@RestController
@RequestMapping("/auth/teacher-sessions")
public class TeacherSessionController {

    private final TeacherLoginService teacherLoginService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public TeacherSessionController(
        TeacherLoginService teacherLoginService,
        RefreshCookieFactory refreshCookieFactory,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.teacherLoginService = teacherLoginService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeacherSessionResponse>> create(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody TeacherSessionRequest request
    ) {
        TeacherLoginResult result = teacherLoginService.login(request.idToken());
        TeacherSessionResponse data = new TeacherSessionResponse(
            result.accessToken(),
            result.refreshToken(),
            result.accessTokenExpiresAt(),
            result.sessionId(),
            result.teacherId(),
            result.displayName(),
            "TCH"
        );
        ApiResponse<TeacherSessionResponse> body = new ApiResponse<>(
            data,
            new ApiMeta(
                requestId,
                correlationId == null ? uuidGenerator.generate() : correlationId,
                clock.instant()
            )
        );
        Duration cookieTtl = Duration.between(
            clock.instant(),
            result.refreshTokenExpiresAt()
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookieFactory.create(result.refreshToken(), cookieTtl).toString()
            )
            .body(body);
    }
}
