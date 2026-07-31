package com.b201.ecovillage.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.identity.config.AuthenticationProperties;

@Component
public class IdentitySecretHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec pepperKey;

    public IdentitySecretHasher(AuthenticationProperties properties) {
        this.pepperKey = new SecretKeySpec(
            properties.tokenPepper().getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM
        );
    }

    public String hashDeviceId(UUID deviceId) {
        return hash("device:" + deviceId);
    }

    public String hashRefreshToken(String refreshToken) {
        return hash("refresh:" + refreshToken);
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash identity secret", exception);
        }
    }
}
