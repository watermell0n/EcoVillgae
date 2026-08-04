package com.b201.ecovillage.store.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.character.domain.ClassroomCharacter;
import com.b201.ecovillage.character.repository.ClassroomCharacterRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;
import com.b201.ecovillage.store.domain.InsufficientStoreStockException;
import com.b201.ecovillage.store.domain.StoreOrder;
import com.b201.ecovillage.store.domain.StoreOrderItem;
import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.domain.StoreStock;
import com.b201.ecovillage.store.repository.StoreOrderItemRepository;
import com.b201.ecovillage.store.repository.StoreOrderRepository;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;

@Service
public class StorePurchaseService {

    private static final String ROUTE_KEY = "/classrooms/{classroom_id}/store/orders";
    private static final String STORE_REVENUE_KEY = "STORE_REVENUE";
    private static final String CLASSROOM_FUND_KEY = "CLASSROOM_FUND";

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final StoreProductRepository productRepository;
    private final StoreProductVersionRepository productVersionRepository;
    private final StoreStockRepository stockRepository;
    private final ClassroomCharacterRepository classroomCharacterRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StoreOrderRepository orderRepository;
    private final StoreOrderItemRepository orderItemRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StorePurchaseService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        StoreProductRepository productRepository,
        StoreProductVersionRepository productVersionRepository,
        StoreStockRepository stockRepository,
        ClassroomCharacterRepository classroomCharacterRepository,
        InventoryItemRepository inventoryItemRepository,
        StoreOrderRepository orderRepository,
        StoreOrderItemRepository orderItemRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PolicySnapshotRepository policySnapshotRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        AtomicLedgerTransactionService transactionService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.productRepository = productRepository;
        this.productVersionRepository = productVersionRepository;
        this.stockRepository = stockRepository;
        this.classroomCharacterRepository = classroomCharacterRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.policySnapshotRepository = policySnapshotRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transactionService = transactionService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StorePurchaseResult purchase(StorePurchaseCommand command) {
        validateCommand(command);
        UUID studentId = parseStudentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.PURCHASE_STORE,
            studentId
        );
        Classroom classroom = classroomRepository
            .findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "경제교실을 찾을 수 없습니다."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "활성 경제교실에서만 상품을 구매할 수 있습니다."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "경제교실 상태가 변경되었습니다."
            );
        }
        ClassroomStudent student = studentRepository
            .findById(studentId)
            .filter(candidate ->
                candidate.getClassroomId().equals(command.classroomId())
                    && candidate.getStatus() == ClassroomStudentStatus.ACTIVE
            )
            .orElseThrow(() -> operation(
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED",
                "현재 학급의 본인 계정으로만 구매할 수 있습니다."
            ));

        String requestHash = requestHash(command);
        IdempotencyRecord replay = idempotencyRecordRepository
            .findForReplay(
                command.classroomId(),
                student.getId(),
                ROUTE_KEY,
                command.requestId().toString()
            )
            .orElse(null);
        if (replay != null) {
            if (!replay.hasRequestHash(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            StoreOrder existing = orderRepository
                .findByClassroomIdAndTransactionGroupId(
                    command.classroomId(),
                    replay.getTransactionGroupId()
                )
                .orElseThrow(() -> operation(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_RESULT_MISSING",
                    "이전 구매 결과를 찾을 수 없습니다."
                ));
            return result(existing);
        }

        Instant now = clock.instant();
        StoreProduct product = productRepository
            .findByClassroomIdAndId(command.classroomId(), command.productId())
            .filter(StoreProduct::isPublished)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_AVAILABLE",
                "현재 판매 중인 상품이 아닙니다."
            ));
        StoreProductVersion productVersion = productVersionRepository
            .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullAndPublishedAtLessThanEqualOrderByVersionDesc(
                command.classroomId(),
                product.getId(),
                now
            )
            .filter(version -> version.isPurchasableAt(now))
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "PRODUCT_NOT_AVAILABLE",
                "현재 구매 가능한 상품 버전이 없습니다."
            ));
        if (productVersion.getVersion() != command.expectedProductVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_CONFLICT",
                "상품 가격 또는 부가세가 변경되었습니다."
            );
        }
        StoreStock stock = stockRepository
            .findForUpdate(command.classroomId(), command.productId())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "OUT_OF_STOCK",
                "상품 재고 정보를 찾을 수 없습니다."
            ));

        ClassroomCharacter classroomCharacter =
            productVersion.getProductKind() == StoreProductKind.CHARACTER
                ? classroomCharacterRepository
                    .findByClassroomIdAndStoreProductId(
                        command.classroomId(),
                        product.getId()
                    )
                    .orElseThrow(() -> operation(
                        HttpStatus.CONFLICT,
                        "CHARACTER_PRODUCT_NOT_CONFIGURED",
                        "캐릭터 상품 연결 정보를 찾을 수 없습니다."
                    ))
                : null;
        if (classroomCharacter != null && command.quantity() != 1) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "캐릭터 상품은 한 번에 1개만 구매할 수 있습니다."
            );
        }
        if (
            classroomCharacter != null
                && inventoryItemRepository
                    .existsByClassroomIdAndClassroomStudentIdAndCharacterCatalogId(
                        command.classroomId(),
                        student.getId(),
                        classroomCharacter.getCharacterCatalogId()
                    )
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "ALREADY_OWNED",
                "이미 보유한 캐릭터 아이템입니다."
            );
        }
        if (stock.getAvailableQuantity() < command.quantity()) {
            throw new InsufficientStoreStockException(
                stock.getAvailableQuantity(),
                command.quantity()
            );
        }

        long subtotal;
        long tax;
        long total;
        try {
            subtotal = Math.multiplyExact(productVersion.getUnitPrice(), command.quantity());
            tax = BigDecimal.valueOf(subtotal)
                .multiply(BigDecimal.valueOf(productVersion.getVatRateBps()))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
            total = Math.addExact(subtotal, tax);
        } catch (ArithmeticException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AMOUNT_OVERFLOW",
                "상품 구매 금액이 허용 범위를 초과했습니다."
            );
        }
        if (total <= 0) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "무료 상품 구매는 현재 공식 상점 결제에서 지원하지 않습니다."
            );
        }

        Map<String, Object> snapshotValues = Map.of(
            "store_product_id", product.getId(),
            "store_product_version_id", productVersion.getId(),
            "product_version", productVersion.getVersion(),
            "product_kind", productVersion.getProductKind().name(),
            "unit_price", productVersion.getUnitPrice(),
            "vat_rate_bps", productVersion.getVatRateBps(),
            "quantity", command.quantity(),
            "subtotal_amount", subtotal,
            "tax_amount", tax,
            "total_amount", total
        );
        String snapshotHash = sha256(
            command.classroomId() + "|" + productVersion.getId() + "|"
                + command.quantity() + "|" + subtotal + "|" + tax
        );
        PolicySnapshot snapshot = policySnapshotRepository
            .findByClassroomIdAndSnapshotHash(command.classroomId(), snapshotHash)
            .orElseGet(() -> policySnapshotRepository.save(
                PolicySnapshot.capture(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    snapshotHash,
                    "1",
                    snapshotValues,
                    "HALF_UP",
                    now
                )
            ));

        LedgerAccount wallet = ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                command.classroomId(),
                student.getId(),
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.studentWallet(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    student.getId(),
                    now
                )
            ));
        LedgerAccount revenue = systemAccount(
            command.classroomId(),
            STORE_REVENUE_KEY,
            LedgerAccountType.STORE_REVENUE,
            now
        );
        LedgerAccount fund = tax == 0
            ? null
            : systemAccount(
                command.classroomId(),
                CLASSROOM_FUND_KEY,
                LedgerAccountType.CLASSROOM_FUND,
                now
            );
        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(new LedgerTransactionPosting(wallet.getId(), -total));
        postings.add(new LedgerTransactionPosting(revenue.getId(), subtotal));
        if (fund != null) {
            postings.add(new LedgerTransactionPosting(fund.getId(), tax));
        }

        UUID transactionGroupId = uuidGenerator.generate();
        UUID orderId = uuidGenerator.generate();
        StoreOrder order = StoreOrder.purchased(
            orderId,
            command.classroomId(),
            student.getId(),
            subtotal,
            tax,
            snapshot.getId(),
            transactionGroupId,
            productVersion.getProductKind(),
            now
        );
        StoreOrderItem item = StoreOrderItem.purchased(
            uuidGenerator.generate(),
            command.classroomId(),
            orderId,
            productVersion.getId(),
            command.quantity(),
            productVersion.getUnitPrice(),
            productVersion.getVatRateBps(),
            tax,
            now
        );
        InventoryItem acquiredCharacter = classroomCharacter == null
            ? null
            : InventoryItem.acquire(
                uuidGenerator.generate(),
                command.classroomId(),
                student.getId(),
                classroomCharacter.getCharacterCatalogId(),
                item.getId(),
                now
            );
        LedgerDomainMutation purchaseMutation = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (stock.getAvailableQuantity() < command.quantity()) {
                    throw new InsufficientStoreStockException(
                        stock.getAvailableQuantity(),
                        command.quantity()
                    );
                }
            }

            @Override
            public void apply() {
                stock.consumeAvailable(command.quantity(), now);
                stockRepository.save(stock);
                orderRepository.save(order);
                orderItemRepository.save(item);
                if (acquiredCharacter != null) {
                    inventoryItemRepository.save(acquiredCharacter);
                }
            }
        };

        transactionService.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                command.classroomId(),
                "STORE_PURCHASE",
                LedgerActorType.STUDENT,
                student.getId(),
                snapshot.getId(),
                ROUTE_KEY,
                command.requestId().toString(),
                requestHash,
                command.correlationId().toString(),
                "공식 상점 상품 구매",
                postings,
                now
            ),
            List.of(purchaseMutation)
        );
        return result(order);
    }

    private LedgerAccount systemAccount(
        UUID classroomId,
        String ownerKey,
        LedgerAccountType type,
        Instant now
    ) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                ownerKey,
                type,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.systemAccount(
                    uuidGenerator.generate(),
                    classroomId,
                    ownerKey,
                    type,
                    now
                )
            ));
    }

    private static StorePurchaseResult result(StoreOrder order) {
        return new StorePurchaseResult(
            order.getId(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getStateVersion()
        );
    }

    private static UUID parseStudentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "학생 인증 정보를 확인할 수 없습니다."
            );
        }
    }

    private static String requestHash(StorePurchaseCommand command) {
        return sha256(
            command.classroomId() + "|" + command.productId() + "|"
                + command.quantity() + "|" + command.expectedProductVersion()
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
            throw new IllegalStateException("구매 요청 해시를 생성할 수 없습니다.", exception);
        }
    }

    private static void validateCommand(StorePurchaseCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.productId() == null
                || command.quantity() <= 0
                || command.expectedProductVersion() < 1
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "상품 구매 요청 값을 확인해 주세요."
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
