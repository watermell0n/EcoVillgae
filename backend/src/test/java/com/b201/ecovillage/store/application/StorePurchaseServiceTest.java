package com.b201.ecovillage.store.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.b201.ecovillage.character.repository.ClassroomCharacterRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionResult;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;
import com.b201.ecovillage.store.domain.StoreOrder;
import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.domain.StoreStock;
import com.b201.ecovillage.store.repository.StoreOrderItemRepository;
import com.b201.ecovillage.store.repository.StoreOrderRepository;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;

class StorePurchaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private ClassroomAuthorizationService authorizationService;
    private ClassroomRepository classroomRepository;
    private ClassroomStudentRepository studentRepository;
    private StoreProductRepository productRepository;
    private StoreProductVersionRepository productVersionRepository;
    private StoreStockRepository stockRepository;
    private ClassroomCharacterRepository classroomCharacterRepository;
    private InventoryItemRepository inventoryItemRepository;
    private StoreOrderRepository orderRepository;
    private StoreOrderItemRepository orderItemRepository;
    private LedgerAccountRepository ledgerAccountRepository;
    private PolicySnapshotRepository policySnapshotRepository;
    private IdempotencyRecordRepository idempotencyRecordRepository;
    private AtomicLedgerTransactionService transactionService;
    private StorePurchaseService service;
    private StoreStock stock;
    private StorePurchaseCommand command;
    private AtomicReference<StoreOrder> savedOrder;

    @BeforeEach
    void setUp() {
        authorizationService = mock(ClassroomAuthorizationService.class);
        classroomRepository = mock(ClassroomRepository.class);
        studentRepository = mock(ClassroomStudentRepository.class);
        productRepository = mock(StoreProductRepository.class);
        productVersionRepository = mock(StoreProductVersionRepository.class);
        stockRepository = mock(StoreStockRepository.class);
        classroomCharacterRepository = mock(ClassroomCharacterRepository.class);
        inventoryItemRepository = mock(InventoryItemRepository.class);
        orderRepository = mock(StoreOrderRepository.class);
        orderItemRepository = mock(StoreOrderItemRepository.class);
        ledgerAccountRepository = mock(LedgerAccountRepository.class);
        policySnapshotRepository = mock(PolicySnapshotRepository.class);
        idempotencyRecordRepository = mock(IdempotencyRecordRepository.class);
        transactionService = mock(AtomicLedgerTransactionService.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        service = new StorePurchaseService(
            authorizationService,
            classroomRepository,
            studentRepository,
            productRepository,
            productVersionRepository,
            stockRepository,
            classroomCharacterRepository,
            inventoryItemRepository,
            orderRepository,
            orderItemRepository,
            ledgerAccountRepository,
            policySnapshotRepository,
            idempotencyRecordRepository,
            transactionService,
            uuidGenerator,
            clock
        );

        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ClassroomStudent student = ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "eco",
            NOW
        );
        StoreProduct product = StoreProduct.draft(
            productId,
            classroomId,
            "연필",
            "실제 상품",
            StoreProductKind.PHYSICAL,
            UUID.randomUUID(),
            NOW
        );
        product.publish(NOW);
        StoreProductVersion version = StoreProductVersion.publish(
            UUID.randomUUID(),
            classroomId,
            productId,
            3,
            StoreProductKind.PHYSICAL,
            1_000,
            1_000,
            true,
            Map.of(),
            NOW
        );
        stock = StoreStock.initialize(productId, classroomId, 3, NOW);
        command = new StorePurchaseCommand(
            studentId.toString(),
            classroomId,
            requestId,
            UUID.randomUUID(),
            productId,
            2,
            3,
            0L
        );

        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(
            idempotencyRecordRepository.findForReplay(
                eq(classroomId),
                eq(studentId),
                any(),
                eq(requestId.toString())
            )
        ).thenReturn(Optional.empty());
        when(productRepository.findByClassroomIdAndId(classroomId, productId))
            .thenReturn(Optional.of(product));
        when(
            productVersionRepository
                .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullAndPublishedAtLessThanEqualOrderByVersionDesc(
                    classroomId,
                    productId,
                    NOW
                )
        ).thenReturn(Optional.of(version));
        when(stockRepository.findForUpdate(classroomId, productId))
            .thenReturn(Optional.of(stock));
        when(policySnapshotRepository.findByClassroomIdAndSnapshotHash(eq(classroomId), any()))
            .thenReturn(Optional.empty());
        when(policySnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(
            ledgerAccountRepository
                .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                    eq(classroomId),
                    eq(studentId),
                    eq(LedgerAccountType.STUDENT_WALLET),
                    eq(LedgerAccountStatus.ACTIVE)
                )
        ).thenReturn(Optional.of(
            LedgerAccount.studentWallet(UUID.randomUUID(), classroomId, studentId, NOW)
        ));
        when(
            ledgerAccountRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    eq(classroomId),
                    any(),
                    any(),
                    eq(LedgerAccountStatus.ACTIVE)
                )
        ).thenReturn(Optional.empty());
        when(ledgerAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        savedOrder = new AtomicReference<>();
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            StoreOrder order = invocation.getArgument(0);
            savedOrder.set(order);
            return order;
        });
        doAnswer(invocation -> {
            UUID groupId = invocation.getArgument(0);
            List<?> mutations = invocation.getArgument(2);
            for (Object mutation : mutations) {
                ((LedgerDomainMutation) mutation).validate();
                ((LedgerDomainMutation) mutation).apply();
            }
            return new LedgerTransactionResult(
                groupId,
                TransactionGroupStatus.COMMITTED,
                2,
                false
            );
        }).when(transactionService).execute(any(UUID.class), any(), anyList());
    }

    @Test
    void purchasesWithTaxAndConsumesStockAtomically() {
        StorePurchaseResult result = service.purchase(command);

        assertEquals("PAID", result.status());
        assertEquals(2_200, result.totalAmount());
        assertEquals(1, stock.getAvailableQuantity());
        ArgumentCaptor<LedgerTransactionCommand> ledgerCommand =
            ArgumentCaptor.forClass(LedgerTransactionCommand.class);
        verify(transactionService).execute(
            any(UUID.class),
            ledgerCommand.capture(),
            anyList()
        );
        List<Long> amounts = ledgerCommand.getValue().postings().stream()
            .map(posting -> posting.amount())
            .sorted()
            .toList();
        assertEquals(List.of(-2_200L, 200L, 2_000L), amounts);
        verify(orderItemRepository).save(any());
    }

    @Test
    void replaysSameIdempotencyKeyWithoutConsumingStockAgain() {
        StorePurchaseResult first = service.purchase(command);
        ArgumentCaptor<LedgerTransactionCommand> ledgerCommand =
            ArgumentCaptor.forClass(LedgerTransactionCommand.class);
        verify(transactionService).execute(
            any(UUID.class),
            ledgerCommand.capture(),
            anyList()
        );
        IdempotencyRecord record = IdempotencyRecord.completed(
            UUID.randomUUID(),
            command.classroomId(),
            UUID.fromString(command.studentSubject()),
            "/classrooms/{classroom_id}/store/orders",
            command.requestId().toString(),
            ledgerCommand.getValue().requestHash(),
            201,
            Map.of(),
            savedOrder.get().getTransactionGroupId(),
            NOW.plusSeconds(60),
            NOW
        );
        when(
            idempotencyRecordRepository.findForReplay(
                eq(command.classroomId()),
                eq(UUID.fromString(command.studentSubject())),
                any(),
                eq(command.requestId().toString())
            )
        ).thenReturn(Optional.of(record));
        when(
            orderRepository.findByClassroomIdAndTransactionGroupId(
                command.classroomId(),
                savedOrder.get().getTransactionGroupId()
            )
        ).thenReturn(Optional.of(savedOrder.get()));

        StorePurchaseResult replay = service.purchase(command);

        assertEquals(first.orderId(), replay.orderId());
        assertEquals(1, stock.getAvailableQuantity());
        verify(transactionService, times(1)).execute(
            any(UUID.class),
            any(),
            anyList()
        );
    }
}
