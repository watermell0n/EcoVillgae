package com.b201.ecovillage.store.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.character.domain.CharacterCatalog;
import com.b201.ecovillage.character.domain.ClassroomCharacter;
import com.b201.ecovillage.character.repository.CharacterCatalogRepository;
import com.b201.ecovillage.character.repository.ClassroomCharacterRepository;
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
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.domain.StoreStock;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;

@Service
public class StoreProductCommandService {

    private static final String ROUTE_KEY =
        "/classrooms/{classroom_id}/store/product-commands";
    private static final String STORE_POLICY_AREA = "STORE";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private static final long DEFAULT_MAXIMUM_UNIT_PRICE = 1_000_000L;
    private static final int DEFAULT_MAXIMUM_STOCK = 1_000_000;

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final StoreProductRepository productRepository;
    private final StoreProductVersionRepository versionRepository;
    private final StoreStockRepository stockRepository;
    private final ClassroomCharacterRepository classroomCharacterRepository;
    private final CharacterCatalogRepository characterCatalogRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StoreProductCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomPolicyVersionRepository policyRepository,
        StoreProductRepository productRepository,
        StoreProductVersionRepository versionRepository,
        StoreStockRepository stockRepository,
        ClassroomCharacterRepository classroomCharacterRepository,
        CharacterCatalogRepository characterCatalogRepository,
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
        this.stockRepository = stockRepository;
        this.classroomCharacterRepository = classroomCharacterRepository;
        this.characterCatalogRepository = characterCatalogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StoreProductCommandResult execute(StoreProductCommand command) {
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
        String requestHash = hash(command);
        StoreProductCommandResult replay = replay(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        validateClassroom(command, classroom);
        ClassroomPolicyVersion policy = validatePolicyVersion(command);
        validatePolicyValues(command, policy);

        Instant now = clock.instant();
        Execution execution;
        try {
            execution = command.command() == StoreProductCommandType.CREATE
                ? create(command, teacher.getId(), now)
                : revise(command, now);
            productRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE",
                "A product with the same name and type already exists."
            );
        }

        StoreProductCommandResult result = result(
            execution.product(),
            execution.version().getVersion()
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "STORE_PRODUCT_" + command.command().name(),
            "STORE_PRODUCT",
            result.productId(),
            command.requestId(),
            command.correlationId(),
            execution.beforeSummary(),
            summary(command, result),
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
                    "product_id", result.productId(),
                    "status", result.status(),
                    "state_version", result.stateVersion(),
                    "version", result.version()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
        return result;
    }

    private Execution create(
        StoreProductCommand command,
        UUID teacherId,
        Instant now
    ) {
        if (command.productId() != null || command.expectedProductVersion() != null) {
            throw validation("CREATE must not include product_id or expected_product_version.");
        }
        UUID productId = uuidGenerator.generate();
        StoreProduct product = StoreProduct.draft(
            productId,
            command.classroomId(),
            command.name(),
            command.description(),
            command.productKind(),
            teacherId,
            now
        );
        if (command.imageUrl() != null) {
            product.updateImage(command.imageUrl(), now);
        }
        productRepository.save(product);
        attachApprovedCharacter(command, productId);
        StoreProductVersion version = versionRepository.save(version(
            command,
            productId,
            1,
            false,
            now
        ));
        stockRepository.save(StoreStock.initialize(
            productId,
            command.classroomId(),
            command.stockQuantity(),
            now
        ));
        return new Execution(product, version, null);
    }

    private Execution revise(StoreProductCommand command, Instant now) {
        StoreProduct product = requireProduct(command);
        StoreProductVersion latest = requireLatestVersion(command, product.getId());
        requireExpectedProductVersion(command, latest.getVersion());
        if (product.getProductKind() != command.productKind()) {
            throw operation(
                HttpStatus.CONFLICT,
                "PRODUCT_TYPE_CONFLICT",
                "The product type cannot be changed."
            );
        }
        Map<String, Object> before = summary(product, latest);
        StoreStock stock = stockRepository
            .findForUpdate(command.classroomId(), product.getId())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "PRODUCT_STOCK_MISSING",
                "The product stock record could not be found."
            ));
        if (command.command() == StoreProductCommandType.DELETE) {
            product.retire(now);
            retireIfLive(latest, now);
            stock.replaceAvailableQuantity(0, now);
            productRepository.save(product);
            stockRepository.save(stock);
            return new Execution(product, latest, before);
        }

        product.updateDescription(command.name(), command.description(), now);
        if (command.imageUrl() != null) {
            product.updateImage(command.imageUrl(), now);
        }
        stock.replaceAvailableQuantity(command.stockQuantity(), now);
        attachApprovedCharacter(command, product.getId());

        boolean purchasable = switch (command.command()) {
            case UPDATE -> product.isPublished();
            case PUBLISH -> {
                try {
                    product.publish(now);
                } catch (IllegalStateException exception) {
                    throw invalidTransition(exception.getMessage());
                }
                yield true;
            }
            case UNPUBLISH -> {
                try {
                    product.unpublish(now);
                } catch (IllegalStateException exception) {
                    throw invalidTransition(exception.getMessage());
                }
                yield false;
            }
            case DELETE -> throw validation("DELETE must retire an existing product.");
            case CREATE -> throw validation("CREATE must create a new product.");
        };
        retireIfLive(latest, now);
        StoreProductVersion next = versionRepository.save(version(
            command,
            product.getId(),
            latest.getVersion() + 1,
            purchasable,
            now
        ));
        productRepository.save(product);
        stockRepository.save(stock);
        return new Execution(product, next, before);
    }

    private StoreProduct requireProduct(StoreProductCommand command) {
        if (command.productId() == null) {
            throw validation("This command requires product_id.");
        }
        return productRepository
            .findByClassroomIdAndId(command.classroomId(), command.productId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The store product could not be found."
            ));
    }

    private StoreProductVersion requireLatestVersion(
        StoreProductCommand command,
        UUID productId
    ) {
        // 은퇴한 버전만 남은 상품도 교사가 고치고 지울 수 있어야 한다. V1_27이 판매 종료
        // 캐릭터의 버전을 은퇴시킨 뒤로 그 상품들이 PRODUCT_VERSION_MISSING으로 막혀 있었다.
        return versionRepository
            .findTopByClassroomIdAndStoreProductIdOrderByVersionDesc(
                command.classroomId(),
                productId
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_MISSING",
                "The store product has no current version."
            ));
    }

    private StoreProductVersion version(
        StoreProductCommand command,
        UUID productId,
        int version,
        boolean purchasable,
        Instant now
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (command.characterSourceId() != null) {
            metadata.put("character_source_id", command.characterSourceId().toString());
        }
        return StoreProductVersion.publish(
            uuidGenerator.generate(),
            command.classroomId(),
            productId,
            version,
            command.productKind(),
            command.unitPrice(),
            command.vatRateBps(),
            purchasable,
            metadata,
            now
        );
    }

    private void attachApprovedCharacter(StoreProductCommand command, UUID productId) {
        if (command.productKind() != StoreProductKind.CHARACTER) {
            if (command.characterSourceId() != null) {
                throw validation("character_source_id is allowed only for CHARACTER products.");
            }
            return;
        }
        ClassroomCharacter classroomCharacter = findClassroomCharacter(command, productId);
        CharacterCatalog catalog = characterCatalogRepository
            .findById(classroomCharacter.getCharacterCatalogId())
            .orElseThrow(StoreProductCommandService::characterNotApproved);
        if (!classroomCharacter.isApproved() || !catalog.isApproved()) {
            throw characterNotApproved();
        }
        try {
            classroomCharacter.attachStoreProduct(productId);
        } catch (IllegalStateException exception) {
            throw characterNotApproved();
        }
        classroomCharacterRepository.save(classroomCharacter);
    }

    private ClassroomCharacter findClassroomCharacter(
        StoreProductCommand command,
        UUID productId
    ) {
        if (command.characterSourceId() == null) {
            return classroomCharacterRepository
                .findByClassroomIdAndStoreProductId(command.classroomId(), productId)
                .orElseThrow(StoreProductCommandService::characterNotApproved);
        }
        return classroomCharacterRepository
            .findByClassroomIdAndId(command.classroomId(), command.characterSourceId())
            .or(() -> classroomCharacterRepository
                .findByClassroomIdAndCharacterCatalogId(
                    command.classroomId(),
                    command.characterSourceId()
                ))
            .orElseThrow(StoreProductCommandService::characterNotApproved);
    }

    private void validateClassroom(StoreProductCommand command, Classroom classroom) {
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Store products can be changed only in an active classroom."
            );
        }
        if (command.expectedStateVersion() != classroom.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }
    }

    private ClassroomPolicyVersion validatePolicyVersion(StoreProductCommand command) {
        ClassroomPolicyVersion current = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                STORE_POLICY_AREA
            )
            .orElse(null);
        int currentVersion = current == null ? 0 : current.getVersion();
        if (command.expectedPolicyVersion() != currentVersion) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "The store policy version has changed."
            );
        }
        return current;
    }

    private static void validatePolicyValues(
        StoreProductCommand command,
        ClassroomPolicyVersion policy
    ) {
        Map<String, Object> values = policy == null ? Map.of() : policy.getPolicyValues();
        long minimumPrice = policyLong(
            values,
            0L,
            "minimum_unit_price",
            "allowed_min_price",
            "allowed_min_value"
        );
        long maximumPrice = policyLong(
            values,
            DEFAULT_MAXIMUM_UNIT_PRICE,
            "maximum_unit_price",
            "allowed_max_price",
            "allowed_max_value",
            "system_amount_cap"
        );
        long maximumStock = policyLong(
            values,
            DEFAULT_MAXIMUM_STOCK,
            "maximum_stock_quantity",
            "maximum_stock"
        );
        long minimumVat = policyLong(values, 0L, "minimum_vat_rate_bps");
        long maximumVat = policyLong(values, 10_000L, "maximum_vat_rate_bps");
        if (
            command.unitPrice() < 0
                || command.unitPrice() < minimumPrice
                || command.unitPrice() > maximumPrice
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PRICE_OUT_OF_RANGE",
                "The product price is outside the allowed range."
            );
        }
        if (command.stockQuantity() < 0 || command.stockQuantity() > maximumStock) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_STOCK",
                "The product stock is outside the allowed range."
            );
        }
        if (
            command.vatRateBps() < 0
                || command.vatRateBps() > 10_000
                || command.vatRateBps() < minimumVat
                || command.vatRateBps() > maximumVat
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_TAX_RATE",
                "The product VAT rate is outside the allowed range."
            );
        }
    }

    private StoreProductCommandResult replay(
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
        return new StoreProductCommandResult(
            responseUuid(body.get("product_id")),
            String.valueOf(body.get("status")),
            responseLong(body.get("state_version")),
            Math.toIntExact(responseLong(body.get("version")))
        );
    }

    private static void validate(StoreProductCommand command) {
        if (
            command == null
                || command.teacherSubject() == null
                || command.teacherSubject().isBlank()
                || command.classroomId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.expectedStateVersion() < 0
                || command.expectedPolicyVersion() < 0
                || command.command() == null
                || command.name() == null
                || command.name().isBlank()
                || command.name().trim().length() > 120
                || command.productKind() == null
        ) {
            throw validation("Check the store product command values.");
        }
        if (command.command() != StoreProductCommandType.CREATE) {
            if (command.productId() == null || command.expectedProductVersion() == null) {
                throw validation(
                    "Modification commands require product_id and expected_product_version."
                );
            }
        }
        if (
            command.expectedProductVersion() != null
                && command.expectedProductVersion() < 1
        ) {
            throw validation("expected_product_version must be positive.");
        }
    }

    private static void requireExpectedProductVersion(
        StoreProductCommand command,
        int currentVersion
    ) {
        if (
            command.expectedProductVersion() == null
                || command.expectedProductVersion() != currentVersion
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_CONFLICT",
                "The store product version has changed."
            );
        }
    }

    private static long policyLong(
        Map<String, Object> values,
        long defaultValue,
        String... keys
    ) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                try {
                    return Long.parseLong(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed optional policy values and keep the system default.
                }
            }
        }
        return defaultValue;
    }

    private static String hash(StoreProductCommand command) {
        return sha256(
            command.classroomId() + "|" + command.command() + "|" + command.productId()
                + "|" + command.name().trim() + "|" + normalized(command.description())
                + "|" + command.productKind() + "|" + command.unitPrice() + "|"
                + command.vatRateBps() + "|" + command.stockQuantity() + "|"
                + command.characterSourceId() + "|" + command.expectedStateVersion()
                + "|" + command.expectedPolicyVersion() + "|"
                + command.expectedProductVersion() + "|" + normalized(command.imageUrl())
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

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static StoreProductCommandResult result(StoreProduct product, int version) {
        return new StoreProductCommandResult(
            product.getId(),
            product.getStatus().name(),
            product.getStateVersion(),
            version
        );
    }

    private static Map<String, Object> summary(
        StoreProduct product,
        StoreProductVersion version
    ) {
        return Map.of(
            "status", product.getStatus().name(),
            "state_version", product.getStateVersion(),
            "version", version.getVersion(),
            "unit_price", version.getUnitPrice(),
            "vat_rate_bps", version.getVatRateBps()
        );
    }

    private static Map<String, Object> summary(
        StoreProductCommand command,
        StoreProductCommandResult result
    ) {
        return Map.of(
            "status", result.status(),
            "state_version", result.stateVersion(),
            "version", result.version(),
            "unit_price", command.unitPrice(),
            "vat_rate_bps", command.vatRateBps(),
            "stock_quantity", command.stockQuantity()
        );
    }

    private static UUID responseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw idempotencyResultMissing();
        }
    }

    private static long responseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw idempotencyResultMissing();
        }
    }

    /**
     * 살아 있는 버전만 은퇴시킨다.
     *
     * <p>V1_27이 판매 종료 캐릭터의 버전을 이미 은퇴시켜 두었다. 그런 상품을 교사가 고치거나
     * 지울 때 다시 은퇴시키려 하면 도메인이 예외를 던지므로, 이미 은퇴한 버전은 건너뛴다.
     * 은퇴 시점을 나중 시각으로 덮어써서 이력을 흐리지 않는 효과도 있다.
     */
    private static void retireIfLive(StoreProductVersion version, Instant now) {
        if (version.getRetiredAt() == null) {
            version.retire(now);
        }
    }

    private static ClassroomOperationException characterNotApproved() {
        return operation(
            HttpStatus.CONFLICT,
            "CHARACTER_NOT_APPROVED",
            "Only an approved classroom character can be sold."
        );
    }

    private static ClassroomOperationException idempotencyResultMissing() {
        return operation(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_RESULT_MISSING",
            "The previous store product result is invalid."
        );
    }

    private static ClassroomOperationException invalidTransition(String message) {
        return operation(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", message);
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

    private record Execution(
        StoreProduct product,
        StoreProductVersion version,
        Map<String, Object> beforeSummary
    ) {
    }
}
