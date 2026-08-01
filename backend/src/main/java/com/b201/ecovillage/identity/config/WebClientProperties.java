package com.b201.ecovillage.identity.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecovillage.web")
public record WebClientProperties(List<String> allowedOrigins) {

    public WebClientProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank())) {
            throw new IllegalArgumentException("allowedOrigins must not contain blank values");
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                "allowedOrigins must be explicit when credentialed cookies are enabled"
            );
        }
    }
}
