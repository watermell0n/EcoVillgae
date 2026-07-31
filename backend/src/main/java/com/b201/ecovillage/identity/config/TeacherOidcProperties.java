package com.b201.ecovillage.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecovillage.teacher-oidc")
public record TeacherOidcProperties(
    boolean enabled,
    String issuer,
    String jwkSetUri,
    String clientId,
    String nameClaim
) {

    public TeacherOidcProperties {
        nameClaim = defaultText(nameClaim, "name");
        if (enabled) {
            requireText(issuer, "issuer");
            requireText(jwkSetUri, "jwkSetUri");
            requireText(clientId, "clientId");
        }
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "ecovillage.teacher-oidc." + field + " must not be blank when OIDC is enabled"
            );
        }
    }
}
