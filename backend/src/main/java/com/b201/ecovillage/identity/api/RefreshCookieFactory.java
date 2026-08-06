package com.b201.ecovillage.identity.api;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.identity.config.AuthenticationProperties;

@Component
public class RefreshCookieFactory {

    private final AuthenticationProperties properties;

    public RefreshCookieFactory(AuthenticationProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String refreshToken, Duration maxAge) {
        return ResponseCookie
            .from(properties.refreshCookieName(), refreshToken)
            .httpOnly(true)
            .secure(properties.refreshCookieSecure())
            .sameSite("Strict")
            // 교사 웹은 백엔드를 자기 오리진의 /backend-api/* 프록시로 호출한다. 브라우저는 쿠키
            // Path를 요청 경로의 접두사로 비교하므로 "/auth"는 "/backend-api/auth/..."와 맞지 않아
            // 갱신 요청에 쿠키가 실리지 않았고, 교사 세션이 액세스 토큰 수명 뒤 되살아나지 못했다.
            // HttpOnly + SameSite=Strict는 그대로 두고 경로만 넓힌다.
            .path("/")
            .maxAge(maxAge)
            .build();
    }

    public ResponseCookie clear() {
        return create("", Duration.ZERO);
    }

    public String cookieName() {
        return properties.refreshCookieName();
    }
}
