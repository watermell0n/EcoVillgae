package com.b201.ecovillage.identity.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebClientCorsConfiguration implements WebMvcConfigurer {

    private final WebClientProperties properties;

    public WebClientCorsConfiguration(WebClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Applies CORS before application authentication filters. Without this,
     * an expired Bearer token can produce a 401 before MVC's CORS handling and
     * browsers surface that response as a generic "Failed to fetch" error.
     */
    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Correlation-Id",
            "X-Refresh-Token",
            "Accept-Language"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
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
