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
            .path("/auth")
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
