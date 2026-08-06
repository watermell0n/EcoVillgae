package com.b201.ecovillage.finance.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.classroom.application.RatePolicySynchronizer;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class FinancialProductCommandService {

    private static final String ROUTE_KEY =
        "/classrooms/{classroom_id}/financial-product-commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final FinancialProductRepository productRepository;
    private final FinancialProductVersionRepository versionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RealtimeEventPublisher eventPublisher;
    private final RatePolicySynchronizer ratePolicySynchronizer;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public FinancialProductCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        FinancialProductRepository productRepository,
        FinancialProductVersionRepository versionRepository,
        IdempotencyRecordRepository idempotencyRepository,
        RealtimeEventPublisher eventPublisher,
        RatePolicySynchronizer ratePolicySynchronizer,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.ratePolicySynchronizer = ratePolicySynchronizer;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public FinancialResourceResult execute(FinancialProductCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_FINANCIAL_PRODUCTS,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
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
                "Financial products can be changed only in an active classroom."
            );
        }
        if (command.productType() == com.b201.ecovillage.finance.domain.FinancialProductType.SAVINGS
            && classroom.getCourseSize() != CourseSize.SEMESTER) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "MODULE_NOT_AVAILABLE_FOR_COURSE_SIZE",
                "정기적금 상품은 한 학기 운영에서만 관리할 수 있습니다."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }
        String requestHash = hash(command);
        FinancialResourceResult replay = replay(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        Instant now = clock.instant();
        FinancialProduct product = switch (command.command()) {
            case CREATE -> create(command, now);
            case UPDATE -> revise(command, false, now);
            case PUBLISH -> revise(command, true, now);
            case STOP -> stop(command, now);
        };
        productRepository.flush();
        // 운영 관리에서 저장한 이율을 AI 설정의 적용값과 같게 유지합니다.
        ratePolicySynchronizer.sync(
            command.classroomId(),
            command.productType() == com.b201.ecovillage.finance.domain.FinancialProductType.SAVINGS
                ? "SAVINGS_RATE"
                : "DEPOSIT_RATE",
            command.interestRateBps(),
            now
        );
        FinancialResourceResult result = result(product);
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
                    "financial_resource_id", result.financialResourceId(),
                    "status", result.status(),
                    "state_version", result.stateVersion()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
        eventPublisher.publish(
            command.classroomId(),
            "ECONOMY",
            null,
            "finance.product.changed",
            "FINANCIAL_PRODUCT",
            product.getId(),
            product.getStateVersion(),
            Map.of(
                "product_id", product.getId().toString(),
                "product_type", product.getProductType().name(),
                "status", product.getStatus()
            ),
            true
        );
        return result;
    }

    private FinancialProduct create(FinancialProductCommand command, Instant now) {
        if (command.productId() != null) {
            throw validation("CREATE must not include product_id.");
        }
        UUID productId = uuidGenerator.generate();
        FinancialProduct product = FinancialProduct.draft(
            productId,
            command.classroomId(),
            command.productType(),
            command.name(),
            null,
            now
        );
        productRepository.save(product);
        versionRepository.save(version(command, productId, 1, false, now));
        return product;
    }

    private FinancialProduct revise(
        FinancialProductCommand command,
        boolean publish,
        Instant now
    ) {
        FinancialProduct product = requireProduct(command);
        FinancialProductVersion latest = versionRepository
            .findTopByClassroomIdAndFinancialProductIdOrderByVersionDesc(
                command.classroomId(),
                product.getId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_MISSING",
                "The financial product has no version."
            ));
        requireExpectedProductVersion(command, latest.getVersion());
        product.revise(command.name(), null, now);
        versionRepository.save(
            version(command, product.getId(), latest.getVersion() + 1, publish, now)
        );
        if (publish) {
            product.publish(now);
        }
        return productRepository.save(product);
    }

    private FinancialProduct stop(FinancialProductCommand command, Instant now) {
        FinancialProduct product = requireProduct(command);
        FinancialProductVersion latest = versionRepository
            .findTopByClassroomIdAndFinancialProductIdOrderByVersionDesc(
                command.classroomId(),
                product.getId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_MISSING",
                "The financial product has no version."
            ));
        requireExpectedProductVersion(command, latest.getVersion());
        product.stop(now);
        return productRepository.save(product);
    }

    private FinancialProduct requireProduct(FinancialProductCommand command) {
        if (command.productId() == null) {
            throw validation("This command requires product_id.");
        }
        FinancialProduct product = productRepository
            .findByClassroomIdAndId(command.classroomId(), command.productId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The financial product could not be found."
            ));
        if (product.getProductType() != command.productType()) {
            throw operation(
                HttpStatus.CONFLICT,
                "PRODUCT_TYPE_CONFLICT",
                "The product type cannot be changed."
            );
        }
        return product;
    }

    private FinancialProductVersion version(
        FinancialProductCommand command,
        UUID productId,
        int version,
        boolean published,
        Instant now
    ) {
        try {
            return FinancialProductVersion.issue(
                uuidGenerator.generate(),
                command.classroomId(),
                productId,
                version,
                command.productType(),
                command.termUnits(),
                command.interestRateBps(),
                command.earlyTerminationRateBps(),
                command.installmentCount(),
                command.installmentAmount(),
                command.minimumAmount(),
                command.maximumAmount(),
                published,
                now
            );
        } catch (IllegalArgumentException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_RATE",
                exception.getMessage()
            );
        }
    }

    private FinancialResourceResult replay(
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
        UUID productId = responseUuid(
            record.getResponseBody().get("financial_resource_id")
        );
        FinancialProduct product = productRepository
            .findByClassroomIdAndId(classroomId, productId)
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous financial product result could not be found."
            ));
        return result(product);
    }

    private static void validate(FinancialProductCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.command() == null
                || command.productType() == null
                || command.name() == null
                || command.name().isBlank()
                || command.termUnits() < 1
                || command.interestRateBps() < 0
                || command.interestRateBps() > 10_000
                || command.earlyTerminationRateBps() < 0
                || command.earlyTerminationRateBps() > 10_000
                || command.minimumAmount() < 1
                || command.maximumAmount() < command.minimumAmount()
        ) {
            throw validation("Check the financial product command values.");
        }
    }

    private static void requireExpectedProductVersion(
        FinancialProductCommand command,
        int current
    ) {
        if (
            command.expectedProductVersion() == null
                || command.expectedProductVersion() != current
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_CONFLICT",
                "The financial product version has changed."
            );
        }
    }

    private static String hash(FinancialProductCommand command) {
        return sha256(
            command.classroomId() + "|" + command.command() + "|" + command.productId()
                + "|" + command.productType() + "|" + command.name().trim() + "|"
                + command.termUnits() + "|" + command.interestRateBps() + "|"
                + command.earlyTerminationRateBps() + "|" + command.installmentCount()
                + "|" + command.installmentAmount() + "|" + command.minimumAmount()
                + "|" + command.maximumAmount() + "|" + command.expectedProductVersion()
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the product command.", exception);
        }
    }

    private static UUID responseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous product result is invalid."
            );
        }
    }

    private static FinancialResourceResult result(FinancialProduct product) {
        return new FinancialResourceResult(
            product.getId(),
            product.getStatus(),
            product.getStateVersion()
        );
    }

    private static ClassroomOperationException validation(String message) {
        return operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
