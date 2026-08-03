package com.b201.ecovillage.realtime.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

final class RealtimeTicketSecrets {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private RealtimeTicketSecrets() {
    }

    static String newSecret(SecureRandom secureRandom) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    static String hash(String rawTicket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return ENCODER.encodeToString(
                digest.digest(rawTicket.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
