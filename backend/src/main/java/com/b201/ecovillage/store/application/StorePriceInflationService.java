package com.b201.ecovillage.store.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;

/**
 * 학급의 모든 상점 상품 가격을 한 번의 요청으로 올린다.
 *
 * <p>전에는 교사 화면이 상품마다 공개중지·수정·재공개 세 번을 보내서, 상품 14개에 42번의
 * 요청이 오갔다. 상품이 늘수록 그대로 늘어나는 구조였다. 여기서는 새 버전을 직접 만들어
 * 상품당 한 번씩만 쓰고, 공개 상태와 구매 가능 여부는 원래 값을 그대로 물려준다.
 *
 * <p>캐릭터 상품도 포함한다. 시스템이 정한 항목이라 교사가 개별 가격을 고치지는 않지만,
 * 물가가 오르면 함께 올라야 한다. V1_27이 은퇴시킨 판매 종료 상품도 가격은 따라 오르되
 * 은퇴 상태는 유지되어 학생 구매는 계속 막힌다.
 */
@Service
public class StorePriceInflationService {

    private static final String ROUTE_KEY =
        "/classrooms/{classroom_id}/store/price-inflation-commands";
    private static final String STORE_POLICY_AREA = "STORE";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private static final long DEFAULT_MAXIMUM_UNIT_PRICE = 1_000_000L;
    /** 한 번에 올릴 수 있는 상한. 1000%를 넘기는 인상은 실수로 본다. */
    private static final int MAX_RATE_BPS = 100_000;

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final StoreProductRepository productRepository;
    private final StoreProductVersionRepository versionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StorePriceInflationService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomPolicyVersionRepository policyRepository,
        StoreProductRepository productRepository,
        StoreProductVersionRepository versionRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.policyRepository = policyRepository;
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StorePriceInflationResult apply(StorePriceInflationCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_STORE_PRODUCTS,
            null
        );
        UserAccount teacher = userRepository
            .findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Store prices can be changed only in an active classroom."
            );
        }
        if (command.expectedStateVersion() != classroom.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }

        // 같은 Idempotency-Key로 다시 들어오면 두 번 올리지 않고 지난 결과를 돌려준다.
        String requestHash = hash(command);
        StorePriceInflationResult replay = replay(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        ClassroomPolicyVersion policy = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                STORE_POLICY_AREA
            )
            .orElse(null);
        int currentPolicyVersion = policy == null ? 0 : policy.getVersion();
        if (command.expectedPolicyVersion() != currentPolicyVersion) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "The store policy version has changed."
            );
        }
        long maximumPrice = maximumUnitPrice(policy);

        Instant now = clock.instant();
        List<StoreProductVersion> created = new ArrayList<>();
        int clamped = 0;
        int unchanged = 0;
        for (StoreProduct product : productRepository
            .findAllByClassroomIdOrderByName(command.classroomId())) {
            StoreProductVersion latest = versionRepository
                .findTopByClassroomIdAndStoreProductIdOrderByVersionDesc(
                    command.classroomId(),
                    product.getId()
                )
                .orElse(null);
            if (latest == null) {
                continue;
            }
            long raised = raise(latest.getUnitPrice(), command.rateBps());
            if (raised > maximumPrice) {
                raised = maximumPrice;
                clamped++;
            }
            if (raised == latest.getUnitPrice()) {
                unchanged++;
                continue;
            }
            StoreProductVersion next = StoreProductVersion.publish(
                uuidGenerator.generate(),
                command.classroomId(),
                product.getId(),
                latest.getVersion() + 1,
                latest.getProductKind(),
                raised,
                latest.getVatRateBps(),
                latest.isPurchasable(),
                latest.getMetadata(),
                now
            );
            // 판매 종료한 상품은 은퇴 상태를 유지해야 학생 구매가 계속 막힌다.
            if (latest.getRetiredAt() != null) {
                next.retire(now);
            } else {
                latest.retire(now);
            }
            created.add(next);
        }
        versionRepository.saveAll(created);

        StorePriceInflationResult result = new StorePriceInflationResult(
            created.size(),
            clamped,
            unchanged,
            classroom.getStateVersion()
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "STORE_PRICE_INFLATION_APPLIED",
            "STORE_PRODUCT",
            command.classroomId(),
            command.requestId(),
            command.correlationId(),
            Map.of("rate_bps", command.rateBps()),
            Map.of(
                "applied_count", result.appliedCount(),
                "clamped_count", result.clampedCount(),
                "unchanged_count", result.unchangedCount()
            ),
            null,
            null
        );
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                command.classroomId(),
                teacher.getId(),
                ROUTE_KEY,
                command.requestId().toString(),
                requestHash,
                HttpStatus.OK.value(),
                Map.of(
                    "applied_count", result.appliedCount(),
                    "clamped_count", result.clampedCount(),
                    "unchanged_count", result.unchangedCount(),
                    "state_version", result.stateVersion()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
        return result;
    }

    /** 인상 후 가격. 화면과 같은 반올림을 쓴다. */
    private static long raise(long unitPrice, int rateBps) {
        return Math.round(unitPrice * (10_000L + rateBps) / 10_000.0);
    }

    private static long maximumUnitPrice(ClassroomPolicyVersion policy) {
        Map<String, Object> values = policy == null ? Map.of() : policy.getPolicyValues();
        for (String key : List.of(
            "maximum_unit_price",
            "allowed_max_price",
            "allowed_max_value",
            "system_amount_cap"
        )) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                try {
                    return Long.parseLong(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // 값이 깨져 있으면 시스템 기본 상한을 쓴다.
                }
            }
        }
        return DEFAULT_MAXIMUM_UNIT_PRICE;
    }

    private StorePriceInflationResult replay(
        UUID classroomId,
        UUID teacherId,
        UUID requestId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(classroomId, teacherId, ROUTE_KEY, requestId.toString())
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Map<String, Object> body = record.getResponseBody();
        return new StorePriceInflationResult(
            intOf(body.get("applied_count")),
            intOf(body.get("clamped_count")),
            intOf(body.get("unchanged_count")),
            longOf(body.get("state_version"))
        );
    }

    private static void validate(StorePriceInflationCommand command) {
        if (
            command == null
                || command.teacherSubject() == null
                || command.teacherSubject().isBlank()
                || command.classroomId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.expectedStateVersion() < 0
                || command.expectedPolicyVersion() < 0
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the store price inflation command values."
            );
        }
        if (command.rateBps() <= 0 || command.rateBps() > MAX_RATE_BPS) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "rate_bps must be between 1 and " + MAX_RATE_BPS + "."
            );
        }
    }

    private static String hash(StorePriceInflationCommand command) {
        String value = command.classroomId() + "|" + command.rateBps() + "|"
            + command.expectedStateVersion() + "|" + command.expectedPolicyVersion();
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the inflation command.", exception);
        }
    }

    private static int intOf(Object value) {
        return Math.toIntExact(longOf(value));
    }

    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous inflation result is invalid."
            );
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
