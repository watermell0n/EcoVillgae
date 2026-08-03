package com.b201.ecovillage.classroom.application;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;

import tools.jackson.databind.ObjectMapper;

@Component
public class ClassroomMutationSupport {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ClassroomMutationSupport(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public UserAccount owner(
        String subject,
        UUID classroomId,
        ClassroomPermission permission
    ) {
        authorizationService.authorize(subject, classroomId, permission, null);
        return userRepository.findByExternalSubject(subject)
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
    }

    public Map<String, Object> replay(
        UUID classroomId,
        UUID actorId,
        String route,
        UUID requestId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository.findForReplay(
            classroomId,
            actorId,
            route,
            requestId.toString()
        ).orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        return record.getResponseBody();
    }

    public void remember(
        UUID classroomId,
        UUID actorId,
        String route,
        UUID requestId,
        String requestHash,
        Map<String, Object> responseBody
    ) {
        Instant now = clock.instant();
        idempotencyRepository.save(IdempotencyRecord.completed(
            uuidGenerator.generate(),
            classroomId,
            actorId,
            route,
            requestId.toString(),
            requestHash,
            HttpStatus.OK.value(),
            responseBody,
            null,
            now.plus(IDEMPOTENCY_TTL),
            now
        ));
    }

    public String hash(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(canonical(value));
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash classroom mutation.", exception);
        }
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(item -> items.add(canonical(item)));
            return items;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (
            value == null || value instanceof Number || value instanceof Boolean
                || value instanceof String
        ) {
            return value;
        }
        return value.toString();
    }

    public static Map<String, Object> body(Object... values) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            body.put(String.valueOf(values[index]), values[index + 1]);
        }
        return body;
    }

    public static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
