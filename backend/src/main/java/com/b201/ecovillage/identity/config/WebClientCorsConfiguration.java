package com.b201.ecovillage.identity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebClientCorsConfiguration implements WebMvcConfigurer {

    private final WebClientProperties properties;

    public WebClientCorsConfiguration(WebClientProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/**")
            .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders(
                "Authorization",
                "Content-Type",
                "Idempotency-Key",
                "X-Correlation-Id",
                "X-Refresh-Token",
                "Accept-Language"
            )
            .exposedHeaders("X-Correlation-Id", "Retry-After")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
