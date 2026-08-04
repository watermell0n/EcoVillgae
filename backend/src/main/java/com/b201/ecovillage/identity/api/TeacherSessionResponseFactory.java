package com.b201.ecovillage.identity.api;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.application.TeacherLoginResult;

@Component
class TeacherSessionResponseFactory {

    private final RefreshCookieFactory refreshCookieFactory;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    TeacherSessionResponseFactory(
        RefreshCookieFactory refreshCookieFactory,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.refreshCookieFactory = refreshCookieFactory;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    ResponseEntity<ApiResponse<TeacherSessionResponse>> created(
        UUID requestId,
        UUID correlationId,
        TeacherLoginResult result
    ) {
        TeacherSessionResponse data = new TeacherSessionResponse(
            result.accessToken(),
            result.accessTokenExpiresAt(),
            result.sessionId(),
            result.teacherId(),
            result.displayName(),
            "TCH"
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookieFactory.create(
                    result.refreshToken(),
                    Duration.between(clock.instant(), result.refreshTokenExpiresAt())
                ).toString()
            )
            .body(new ApiResponse<>(
                data,
                new ApiMeta(
                    requestId,
                    correlationId == null ? uuidGenerator.generate() : correlationId,
                    clock.instant()
                )
            ));
    }
}
