package com.b201.bibim.feature.shop;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryEntity;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.shop.PurchaseEntity;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.shop.ShopDto.ProductListResponse;
import com.b201.bibim.feature.shop.ShopDto.ProductResponse;
import com.b201.bibim.feature.shop.ShopDto.PurchaseHistoryItemResponse;
import com.b201.bibim.feature.shop.ShopDto.PurchaseHistoryResponse;
import com.b201.bibim.feature.shop.ShopDto.PurchaseRequest;
import com.b201.bibim.feature.shop.ShopDto.PurchaseResponse;
import com.b201.bibim.feature.shop.ShopDto.TeacherPurchaseResponse;
import com.b201.bibim.feature.shop.ShopDto.TeacherShopSummaryResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {

    private static final java.util.Set<String> OPEN_STAGES = java.util.Set.of(
            "DAY1_FREE_ACTIVITY",
            "DAY2_FINAL_SHOP"
    );

    private final RoomRepository roomRepository;
    private final DaySessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;

    public ShopService(
            RoomRepository roomRepository,
            DaySessionRepository sessionRepository,
            StudentRepository studentRepository,
            ProductRepository productRepository,
            PurchaseRepository purchaseRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository
    ) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.productRepository = productRepository;
        this.purchaseRepository = purchaseRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public ProductListResponse products(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        ensureStoreOpen(room);
        return new ProductListResponse(
                walletBalance(room.getId(), studentId),
                productRepository.findAllByRoomIdOrderByDisplayOrder(room.getId())
                        .stream()
                        .map(this::toProductResponse)
                        .toList()
        );
    }

    @Transactional
    public PurchaseResponse purchase(
            UUID studentId,
            String idempotencyKey,
            PurchaseRequest request
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = roomRepository.findByIdForUpdate(snapshot.getRoomId())
                .orElseThrow(this::roomNotFound);
        StudentEntity student = studentRepository.findByIdForUpdate(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .filter(item -> room.getId().equals(item.getRoomId()))
                .orElseThrow(this::authenticationRequired);

        var existingTransaction = transactionRepository.findByIdempotencyKey(normalizedKey);
        if (existingTransaction.isPresent()) {
            return replayPurchase(existingTransaction.get(), student, request);
        }

        ensureStoreOpen(room);
        AccountEntity wallet = accountRepository.findStudentAccountForUpdate(
                        room.getId(),
                        studentId,
                        "STUDENT_WALLET"
                )
                .orElseThrow(this::insufficientBalance);
        ProductEntity product = productRepository.findByIdForUpdate(request.productId())
                .filter(item -> room.getId().equals(item.getRoomId()))
                .orElseThrow(this::productNotFound);
        ensureProductAvailable(product, request.quantity());

        long totalPrice;
        try {
            totalPrice = Math.multiplyExact(product.getCurrentPrice(), request.quantity());
        } catch (ArithmeticException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PURCHASE_AMOUNT",
                    "구매 금액을 계산할 수 없습니다."
            );
        }
        if (wallet.getBalance() < totalPrice) {
            throw insufficientBalance();
        }

        DaySessionEntity session = sessionRepository
                .findByRoomIdAndDayNumber(room.getId(), (short) room.getCurrentDay())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "SESSION_NOT_FOUND",
                        "현재 수업 세션을 찾을 수 없습니다."
                ));
        AccountEntity shopRevenue = getOrCreateShopRevenue(room.getId());
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.studentPurchase(
                        room.getId(),
                        session.getId(),
                        studentId,
                        normalizedKey,
                        product.getName() + " 구매",
                        Map.of(
                                "productId", product.getId().toString(),
                                "quantity", request.quantity(),
                                "unitPrice", product.getCurrentPrice(),
                                "totalPrice", totalPrice
                        )
                )
        );

        long walletBefore = wallet.getBalance();
        long revenueBefore = shopRevenue.getBalance();
        wallet.changeBy(-totalPrice);
        shopRevenue.changeBy(totalPrice);
        product.sell(request.quantity());
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(
                        transaction.getId(), wallet, -totalPrice, walletBefore, 1
                ),
                FinancialEntryEntity.create(
                        transaction.getId(), shopRevenue, totalPrice, revenueBefore, 2
                )
        ));
        PurchaseEntity purchase = purchaseRepository.save(PurchaseEntity.success(
                room.getId(),
                studentId,
                product.getId(),
                transaction.getId(),
                request.quantity(),
                product.getCurrentPrice(),
                totalPrice
        ));
        room.markEconomicStateChanged();

        return toPurchaseResponse(purchase, product, wallet.getBalance(), false);
    }

    @Transactional(readOnly = true)
    public PurchaseHistoryResponse purchaseHistory(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        Map<UUID, ProductEntity> productsById = productRepository
                .findAllByRoomIdOrderByDisplayOrder(student.getRoomId())
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        List<PurchaseHistoryItemResponse> purchases = purchaseRepository
                .findAllByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(purchase -> {
                    ProductEntity product = productsById.get(purchase.getProductId());
                    return new PurchaseHistoryItemResponse(
                            purchase.getId(),
                            purchase.getProductId(),
                            product == null ? "삭제된 상품" : product.getName(),
                            purchase.getQuantity(),
                            purchase.getUnitPrice(),
                            purchase.getTotalPrice(),
                            purchase.getStatus(),
                            purchase.getCreatedAt()
                    );
                })
                .toList();
        return new PurchaseHistoryResponse(purchases);
    }

    @Transactional(readOnly = true)
    public TeacherShopSummaryResponse teacherSummary(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        List<ProductEntity> products = productRepository
                .findAllByRoomIdOrderByDisplayOrder(roomId);
        Map<UUID, ProductEntity> productsById = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        Map<UUID, StudentEntity> studentsById = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(roomId)
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        List<PurchaseEntity> purchases = purchaseRepository
                .findAllByRoomIdOrderByCreatedAtDesc(roomId);
        List<TeacherPurchaseResponse> recentPurchases = purchases.stream()
                .limit(20)
                .map(purchase -> {
                    StudentEntity student = studentsById.get(purchase.getStudentId());
                    ProductEntity product = productsById.get(purchase.getProductId());
                    return new TeacherPurchaseResponse(
                            purchase.getId(),
                            student == null ? 0 : student.getParticipantNumber(),
                            student == null ? "알 수 없음" : student.getNickname(),
                            product == null ? "삭제된 상품" : product.getName(),
                            purchase.getQuantity(),
                            purchase.getTotalPrice(),
                            purchase.getCreatedAt()
                    );
                })
                .toList();
        long totalRevenue = purchases.stream()
                .filter(purchase -> "SUCCESS".equals(purchase.getStatus()))
                .mapToLong(PurchaseEntity::getTotalPrice)
                .sum();
        return new TeacherShopSummaryResponse(
                room.getId(),
                purchases.size(),
                totalRevenue,
                products.stream().map(this::toProductResponse).toList(),
                recentPurchases
        );
    }

    private PurchaseResponse replayPurchase(
            FinancialTransactionEntity transaction,
            StudentEntity student,
            PurchaseRequest request
    ) {
        if (!"PURCHASE".equals(transaction.getTransactionType())
                || !student.getId().equals(transaction.getStudentId())) {
            throw idempotencyKeyReused();
        }
        PurchaseEntity purchase = purchaseRepository.findByTransactionId(transaction.getId())
                .orElseThrow(this::idempotencyKeyReused);
        if (!request.productId().equals(purchase.getProductId())
                || request.quantity() != purchase.getQuantity()) {
            throw idempotencyKeyReused();
        }
        ProductEntity product = productRepository.findById(purchase.getProductId())
                .orElseThrow(this::productNotFound);
        return toPurchaseResponse(
                purchase,
                product,
                walletBalance(student.getRoomId(), student.getId()),
                true
        );
    }

    private ProductResponse toProductResponse(ProductEntity product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getOriginalPrice(),
                product.getCurrentPrice(),
                product.getInitialStock(),
                product.getCurrentStock(),
                product.getStatus(),
                product.getDisplayOrder()
        );
    }

    private PurchaseResponse toPurchaseResponse(
            PurchaseEntity purchase,
            ProductEntity product,
            long walletBalance,
            boolean replayed
    ) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getProductId(),
                product.getName(),
                purchase.getQuantity(),
                purchase.getUnitPrice(),
                purchase.getTotalPrice(),
                walletBalance,
                product.getCurrentStock(),
                product.getStatus(),
                purchase.getCreatedAt(),
                replayed
        );
    }

    private StudentEntity findActiveStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private RoomEntity findOwnedRoom(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
        if (!room.getTeacherId().equals(teacherId)) {
            throw roomNotFound();
        }
        return room;
    }

    private void ensureStoreOpen(RoomEntity room) {
        if (!OPEN_STAGES.contains(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STORE_CLOSED",
                    "현재는 상품을 구매할 수 있는 시간이 아닙니다."
            );
        }
    }

    private void ensureProductAvailable(ProductEntity product, int quantity) {
        if ("SOLD_OUT".equals(product.getStatus()) || product.getCurrentStock() < quantity) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "OUT_OF_STOCK",
                    "상품 재고가 부족합니다.",
                    Map.of("remainingStock", product.getCurrentStock())
            );
        }
        if (!"ON_SALE".equals(product.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_UNAVAILABLE",
                    "현재 판매 중인 상품이 아닙니다."
            );
        }
    }

    private AccountEntity getOrCreateShopRevenue(UUID roomId) {
        return accountRepository.findRoomAccountForUpdate(roomId, "SHOP_REVENUE")
                .orElseGet(() -> accountRepository.saveAndFlush(
                        AccountEntity.shopRevenue(roomId)
                ));
    }

    private long walletBalance(UUID roomId, UUID studentId) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(
                        roomId,
                        studentId,
                        "STUDENT_WALLET"
                )
                .map(AccountEntity::getBalance)
                .orElse(0L);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key 헤더가 필요합니다."
            );
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key는 100자 이하여야 합니다."
            );
        }
        return normalized;
    }

    private ApiException authenticationRequired() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "로그인이 필요합니다."
        );
    }

    private ApiException roomNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ROOM_NOT_FOUND",
                "수업 방을 찾을 수 없습니다."
        );
    }

    private ApiException productNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_FOUND",
                "상품을 찾을 수 없습니다."
        );
    }

    private ApiException insufficientBalance() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_BALANCE",
                "지갑 잔액이 부족합니다."
        );
    }

    private ApiException idempotencyKeyReused() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                "같은 Idempotency-Key를 다른 구매에 사용할 수 없습니다."
        );
    }
}
