package com.b201.ecovillage.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.identity.config.AuthenticationProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class HmacAccessTokenCodec {

    private static final Base64.Encoder BASE64_URL_ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthenticationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecretKeySpec signingKey;

    public HmacAccessTokenCodec(
        AuthenticationProperties properties,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.signingKey = new SecretKeySpec(
            properties.jwtSecret().getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM
        );
    }

    public String issueStudent(
        UUID sessionId,
        UUID classroomId,
        UUID studentId,
        long credentialVersion,
        Instant issuedAt,
        Instant expiresAt
    ) {
        Map<String, Object> claims = baseClaims(
            studentId.toString(),
            sessionId,
            "STU",
            issuedAt,
            expiresAt
        );
        claims.put("cid", classroomId.toString());
        claims.put("cv", credentialVersion);
        return encode(claims);
    }

    public String issueTeacher(
        UUID sessionId,
        UUID userId,
        Instant issuedAt,
        Instant expiresAt
    ) {
        Map<String, Object> claims = baseClaims(
            userId.toString(),
            sessionId,
            "TCH",
            issuedAt,
            expiresAt
        );
        claims.put("uid", userId.toString());
        return encode(claims);
    }

    public AccessTokenClaims decode(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3) {
                throw new InvalidAccessTokenException();
            }
            String unsignedToken = parts[0] + "." + parts[1];
            byte[] expectedSignature = sign(unsignedToken);
            byte[] actualSignature = BASE64_URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new InvalidAccessTokenException();
            }

            JsonNode header = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[0]));
            JsonNode claims = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[1]));
            if (
                !"HS256".equals(requiredText(header, "alg"))
                    || !properties.issuer().equals(requiredText(claims, "iss"))
            ) {
                throw new InvalidAccessTokenException();
            }

            Instant issuedAt = Instant.ofEpochSecond(requiredLong(claims, "iat"));
            Instant expiresAt = Instant.ofEpochSecond(requiredLong(claims, "exp"));
            Instant now = clock.instant();
            if (!expiresAt.isAfter(now) || issuedAt.isAfter(now.plusSeconds(60))) {
                throw new InvalidAccessTokenException();
            }

            String role = requiredText(claims, "role");
            UUID userId = "TCH".equals(role)
                ? requiredUuid(claims, "uid")
                : null;
            UUID classroomId = "STU".equals(role)
                ? requiredUuid(claims, "cid")
                : null;
            long credentialVersion = "STU".equals(role)
                ? requiredLong(claims, "cv")
                : 0;
            if (!"TCH".equals(role) && !"STU".equals(role)) {
                throw new InvalidAccessTokenException();
            }

            return new AccessTokenClaims(
                requiredText(claims, "sub"),
                requiredUuid(claims, "sid"),
                role,
                userId,
                classroomId,
                credentialVersion,
                issuedAt,
                expiresAt
            );
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidAccessTokenException(exception);
        }
    }

    private Map<String, Object> baseClaims(
        String subject,
        UUID sessionId,
        String role,
        Instant issuedAt,
        Instant expiresAt
    ) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", properties.issuer());
        claims.put("sub", subject);
        claims.put("sid", sessionId.toString());
        claims.put("role", role);
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        return claims;
    }

    private String encode(Map<String, Object> claims) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            String encodedHeader = BASE64_URL_ENCODER.encodeToString(
                objectMapper.writeValueAsBytes(header)
            );
            String encodedClaims = BASE64_URL_ENCODER.encodeToString(
                objectMapper.writeValueAsBytes(claims)
            );
            String unsignedToken = encodedHeader + "." + encodedClaims;
            return unsignedToken + "." + BASE64_URL_ENCODER.encodeToString(sign(unsignedToken));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create access token", exception);
        }
    }

    private byte[] sign(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(signingKey);
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new InvalidAccessTokenException();
        }
        return value.asText();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new InvalidAccessTokenException();
        }
        return value.asLong();
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        return UUID.fromString(requiredText(node, field));
    }
}
