package com.b201.ecovillage.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecovillage.auth")
public record AuthenticationProperties(
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    String jwtSecret,
    String tokenPepper,
    int loginAttemptLimit,
    Duration loginAttemptWindow,
    String refreshCookieName,
    boolean refreshCookieSecure
) {

    public AuthenticationProperties {
        requireText(issuer, "issuer");
        requirePositive(accessTokenTtl, "accessTokenTtl");
        requirePositive(refreshTokenTtl, "refreshTokenTtl");
        requireSecret(jwtSecret, "jwtSecret");
        requireSecret(tokenPepper, "tokenPepper");
        if (loginAttemptLimit <= 0) {
            throw new IllegalArgumentException("loginAttemptLimit must be positive");
        }
        requirePositive(loginAttemptWindow, "loginAttemptWindow");
        requireText(refreshCookieName, "refreshCookieName");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireSecret(String value, String field) {
        requireText(value, field);
        if (value.length() < 32) {
            throw new IllegalArgumentException(field + " must contain at least 32 characters");
        }
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
