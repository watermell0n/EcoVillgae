package com.b201.bibim.global.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

    public static final String TEACHER_COOKIE_NAME = "bibim_teacher_session";
    public static final String STUDENT_COOKIE_NAME = "bibim_student_session";
    public static final String ADMIN_COOKIE_NAME = "bibim_admin_session";

    private final AuthSessionService authSessionService;
    private final boolean secure;

    public AuthCookieService(
            AuthSessionService authSessionService,
            @Value("${app.auth.cookie-secure:false}") boolean secure
    ) {
        this.authSessionService = authSessionService;
        this.secure = secure;
    }

    public ResponseCookie create(AuthRole role, String token) {
        String name = switch (role) {
            case TEACHER -> TEACHER_COOKIE_NAME;
            case STUDENT -> STUDENT_COOKIE_NAME;
            case ADMIN -> ADMIN_COOKIE_NAME;
        };
        Duration maxAge = authSessionService.sessionDuration(role);
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
