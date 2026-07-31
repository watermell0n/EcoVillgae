package com.b201.ecovillage.identity.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.identity.application.TeacherAuthenticationException;
import com.b201.ecovillage.identity.application.TeacherIdentityVerifier;
import com.b201.ecovillage.identity.application.VerifiedTeacherIdentity;
import com.b201.ecovillage.identity.config.TeacherOidcProperties;

@Component
public class OidcTeacherIdentityVerifier implements TeacherIdentityVerifier {

    private final TeacherOidcProperties properties;
    private final JwtDecoder decoder;

    public OidcTeacherIdentityVerifier(TeacherOidcProperties properties) {
        this.properties = properties;
        this.decoder = properties.enabled() ? createDecoder(properties) : null;
    }

    @Override
    public VerifiedTeacherIdentity verify(String idToken) {
        if (!properties.enabled() || decoder == null) {
            throw new TeacherAuthenticationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_PROVIDER_UNAVAILABLE",
                "교사 로그인 제공자가 아직 설정되지 않았습니다.",
                false
            );
        }
        if (idToken == null || idToken.isBlank()) {
            throw invalidIdentity();
        }

        try {
            Jwt jwt = decoder.decode(idToken);
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank() || subject.length() > 128) {
                throw invalidIdentity();
            }
            String displayName = jwt.getClaimAsString(properties.nameClaim());
            return new VerifiedTeacherIdentity(
                subject,
                displayName == null || displayName.isBlank() ? null : displayName.trim()
            );
        } catch (JwtException exception) {
            if (hasCause(exception, IOException.class)) {
                throw new TeacherAuthenticationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_PROVIDER_UNAVAILABLE",
                    "교사 로그인 제공자에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.",
                    true
                );
            }
            throw invalidIdentity();
        }
    }

    private static JwtDecoder createDecoder(TeacherOidcProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(properties.jwkSetUri())
            .build();
        OAuth2TokenValidator<Jwt> issuerValidator =
            JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            List<String> audience = token.getAudience();
            String authorizedParty = token.getClaimAsString("azp");
            boolean audienceMatches =
                audience != null && audience.contains(properties.clientId());
            boolean authorizedPartyMatches =
                audience == null
                    || audience.size() <= 1
                    || properties.clientId().equals(authorizedParty);
            if (audienceMatches && authorizedPartyMatches) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                    "invalid_token",
                    "OIDC ID token audience does not contain the configured client ID",
                    null
                )
            );
        };
        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator)
        );
        return decoder;
    }

    private static TeacherAuthenticationException invalidIdentity() {
        return new TeacherAuthenticationException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_ID_TOKEN",
            "교사 인증 정보가 유효하지 않거나 만료되었습니다.",
            false
        );
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
