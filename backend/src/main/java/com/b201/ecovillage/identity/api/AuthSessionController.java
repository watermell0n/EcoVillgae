package com.b201.ecovillage.identity.api;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.application.CurrentSessionService;
import com.b201.ecovillage.identity.application.CurrentUserView;
import com.b201.ecovillage.identity.application.SessionRefreshResult;
import com.b201.ecovillage.identity.application.SessionRefreshService;
import com.b201.ecovillage.identity.application.TeacherAuthenticationException;
import com.b201.ecovillage.identity.security.EcoVillagePrincipal;

@RestController
@RequestMapping("/auth")
public class AuthSessionController {

    private final SessionRefreshService sessionRefreshService;
    private final CurrentSessionService currentSessionService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AuthSessionController(
        SessionRefreshService sessionRefreshService,
        CurrentSessionService currentSessionService,
        RefreshCookieFactory refreshCookieFactory,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.sessionRefreshService = sessionRefreshService;
        this.currentSessionService = currentSessionService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/token-refreshes")
    public ResponseEntity<ApiResponse<SessionRefreshResponse>> refresh(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @RequestHeader(name = "X-Refresh-Token", required = false) String headerToken,
        HttpServletRequest request
    ) {
        String studentHeaderToken = trimToNull(headerToken);
        String teacherCookieToken = trimToNull(refreshCookie(request));
        if (studentHeaderToken != null && teacherCookieToken != null) {
            throw new TeacherAuthenticationException(
                HttpStatus.BAD_REQUEST,
                "AMBIGUOUS_REFRESH_TOKEN",
                "학생용 갱신 헤더와 교사용 갱신 쿠키를 동시에 보낼 수 없습니다.",
                false
            );
        }
        SessionRefreshResult result = teacherCookieToken != null
            ? sessionRefreshService.refreshTeacher(teacherCookieToken)
            : sessionRefreshService.refreshStudent(studentHeaderToken);
        SessionRefreshResponse data = new SessionRefreshResponse(
            result.accessToken(),
            "STU".equals(result.role()) ? result.refreshToken() : null,
            result.accessTokenExpiresAt(),
            result.sessionId(),
            result.role()
        );
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if ("TCH".equals(result.role())) {
            response.header(
                HttpHeaders.SET_COOKIE,
                refreshCookieFactory.create(
                    result.refreshToken(),
                    Duration.between(clock.instant(), result.refreshTokenExpiresAt())
                ).toString()
            );
        }
        return response.body(
            new ApiResponse<>(
                data,
                meta(requestId, correlationId)
            )
        );
    }

    @DeleteMapping("/sessions/current")
    public ResponseEntity<Void> logout(
        @RequestHeader("Idempotency-Key") UUID requestId,
        Principal principal
    ) {
        currentSessionService.logout(authenticated(principal));
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
            .build();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        CurrentUserView view = currentSessionService.current(authenticated(principal));
        CurrentUserResponse data = new CurrentUserResponse(
            view.sessionId(),
            view.userId(),
            view.displayName(),
            view.role(),
            view.accessTokenExpiresAt(),
            view.schools()
                .stream()
                .map(school -> new SchoolAccessResponse(school.schoolId(), school.role()))
                .toList(),
            view.classrooms()
                .stream()
                .map(classroom -> new ClassroomAccessResponse(
                    classroom.classroomId(),
                    classroom.classroomCode(),
                    classroom.role()
                ))
                .toList()
        );
        return new ApiResponse<>(data, meta(uuidGenerator.generate(), correlationId));
    }

    private String refreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
            .filter(cookie -> refreshCookieFactory.cookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private ApiMeta meta(UUID requestId, UUID correlationId) {
        return new ApiMeta(
            requestId,
            correlationId == null ? uuidGenerator.generate() : correlationId,
            clock.instant()
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static EcoVillagePrincipal authenticated(Principal principal) {
        if (principal instanceof EcoVillagePrincipal authenticated) {
            return authenticated;
        }
        throw new TeacherAuthenticationException(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REQUIRED",
            "로그인이 필요합니다.",
            false
        );
    }
}
