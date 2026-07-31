package com.b201.ecovillage.identity.security;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.identity.config.AuthenticationProperties;

@Component
public class TeacherTokenIssuer {

    private static final Base64.Encoder BASE64_URL =
        Base64.getUrlEncoder().withoutPadding();

    private final AuthenticationProperties properties;
    private final IdentitySecretHasher secretHasher;
    private final SecureRandom secureRandom;
    private final HmacAccessTokenCodec accessTokenCodec;

    public TeacherTokenIssuer(
        AuthenticationProperties properties,
        IdentitySecretHasher secretHasher,
        SecureRandom secureRandom,
        HmacAccessTokenCodec accessTokenCodec
    ) {
        this.properties = properties;
        this.secretHasher = secretHasher;
        this.secureRandom = secureRandom;
        this.accessTokenCodec = accessTokenCodec;
    }

    public IssuedTeacherTokens issue(
        UUID sessionId,
        UUID userId,
        Instant issuedAt
    ) {
        Instant accessExpiresAt = issuedAt.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(properties.refreshTokenTtl());
        String refreshToken = randomRefreshToken();
        return new IssuedTeacherTokens(
            accessTokenCodec.issueTeacher(
                sessionId,
                userId,
                issuedAt,
                accessExpiresAt
            ),
            refreshToken,
            secretHasher.hashRefreshToken(refreshToken),
            accessExpiresAt,
            refreshExpiresAt
        );
    }

    private String randomRefreshToken() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return "tch_" + BASE64_URL.encodeToString(value);
    }
}
