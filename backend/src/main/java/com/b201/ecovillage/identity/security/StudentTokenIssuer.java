package com.b201.ecovillage.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.identity.config.AuthenticationProperties;

import tools.jackson.databind.ObjectMapper;

@Component
public class StudentTokenIssuer {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthenticationProperties properties;
    private final IdentitySecretHasher secretHasher;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;
    private final SecretKeySpec signingKey;

    public StudentTokenIssuer(
        AuthenticationProperties properties,
        IdentitySecretHasher secretHasher,
        ObjectMapper objectMapper,
        SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.secretHasher = secretHasher;
        this.objectMapper = objectMapper;
        this.secureRandom = secureRandom;
        this.signingKey = new SecretKeySpec(
            properties.jwtSecret().getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM
        );
    }

    public IssuedStudentTokens issue(
        UUID sessionId,
        UUID classroomId,
        UUID studentId,
        long credentialVersion,
        Instant issuedAt
    ) {
        Instant accessExpiresAt = issuedAt.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(properties.refreshTokenTtl());
        String refreshToken = randomRefreshToken();

        return new IssuedStudentTokens(
            createAccessToken(
                sessionId,
                classroomId,
                studentId,
                credentialVersion,
                issuedAt,
                accessExpiresAt
            ),
            refreshToken,
            secretHasher.hashRefreshToken(refreshToken),
            accessExpiresAt,
            refreshExpiresAt
        );
    }

    private String createAccessToken(
        UUID sessionId,
        UUID classroomId,
        UUID studentId,
        long credentialVersion,
        Instant issuedAt,
        Instant expiresAt
    ) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", properties.issuer());
        claims.put("sub", studentId.toString());
        claims.put("sid", sessionId.toString());
        claims.put("cid", classroomId.toString());
        claims.put("role", "STU");
        claims.put("cv", credentialVersion);
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());

        try {
            String encodedHeader = BASE64_URL.encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedClaims = BASE64_URL.encodeToString(objectMapper.writeValueAsBytes(claims));
            String unsignedToken = encodedHeader + "." + encodedClaims;
            return unsignedToken + "." + sign(unsignedToken);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create student access token", exception);
        }
    }

    private String sign(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(signingKey);
        return BASE64_URL.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String randomRefreshToken() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return BASE64_URL.encodeToString(value);
    }
}
