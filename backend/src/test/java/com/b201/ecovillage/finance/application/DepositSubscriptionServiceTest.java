package com.b201.ecovillage.finance.application;

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
import java.time.Duration;
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

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductType;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
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

class DepositSubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private DepositAccountRepository accountRepository;
    private IdempotencyRecordRepository idempotencyRepository;
    private AtomicLedgerTransactionService transactionService;
    private DepositSubscriptionService service;
    private DepositSubscriptionCommand command;
    private AtomicReference<DepositAccount> savedAccount;
    private AtomicReference<LedgerTransactionCommand> ledgerCommand;

    @BeforeEach
    void setUp() {
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
        ClassroomStudentRepository studentRepository =
            mock(ClassroomStudentRepository.class);
        FinancialProductRepository productRepository =
            mock(FinancialProductRepository.class);
        FinancialProductVersionRepository versionRepository =
            mock(FinancialProductVersionRepository.class);
        accountRepository = mock(DepositAccountRepository.class);
        LedgerAccountRepository ledgerAccountRepository =
            mock(LedgerAccountRepository.class);
        PolicySnapshotRepository snapshotRepository = mock(PolicySnapshotRepository.class);
        idempotencyRepository = mock(IdempotencyRecordRepository.class);
        transactionService = mock(AtomicLedgerTransactionService.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        service = new DepositSubscriptionService(
            authorizationService,
            classroomRepository,
            studentRepository,
            productRepository,
            versionRepository,
            accountRepository,
            ledgerAccountRepository,
            snapshotRepository,
            idempotencyRepository,
            transactionService,
            uuidGenerator,
            clock
        );

        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        command = new DepositSubscriptionCommand(
            studentId.toString(),
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            0L,
            productId,
            1_000,
            2
        );
        ClassroomStudent student = ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "student",
            NOW.minusSeconds(100)
        );
        FinancialProduct product = FinancialProduct.draft(
            productId,
            classroomId,
            FinancialProductType.DEPOSIT,
            "30-day deposit",
            null,
            NOW.minusSeconds(100)
        );
        product.publish(NOW.minusSeconds(90));
        FinancialProductVersion version = FinancialProductVersion.issue(
            versionId,
            classroomId,
            productId,
            2,
            FinancialProductType.DEPOSIT,
            30,
            500,
            100,
            null,
            null,
            100,
            10_000,
            true,
            NOW.minusSeconds(90)
        );
        LedgerAccount wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            studentId,
            NOW.minusSeconds(100)
        );
        wallet.post(5_000, NOW.minusSeconds(80));
        LedgerAccount liability = LedgerAccount.systemAccount(
            UUID.randomUUID(),
            classroomId,
            "DEPOSIT_LIABILITY",
            LedgerAccountType.DEPOSIT_LIABILITY,
            NOW.minusSeconds(100)
        );
        PolicySnapshot snapshot = PolicySnapshot.capture(
            UUID.randomUUID(),
            classroomId,
            "deposit-snapshot",
            "1",
            Map.of("interest_rate_bps", 500),
            "HALF_UP",
            NOW
        );

        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(
            idempotencyRepository.findForReplay(
                eq(classroomId),
                eq(studentId),
                any(),
                eq(command.requestId().toString())
            )
        ).thenReturn(Optional.empty());
        when(productRepository.findByClassroomIdAndId(classroomId, productId))
            .thenReturn(Optional.of(product));
        when(
            versionRepository
                .findTopByClassroomIdAndFinancialProductIdAndStatusOrderByVersionDesc(
                    classroomId,
                    productId,
                    "PUBLISHED"
                )
        ).thenReturn(Optional.of(version));
        when(snapshotRepository.findByClassroomIdAndSnapshotHash(eq(classroomId), any()))
            .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenReturn(snapshot);
        when(
            ledgerAccountRepository
                .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                    classroomId,
                    studentId,
                    LedgerAccountType.STUDENT_WALLET,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(wallet));
        when(
            ledgerAccountRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId,
                    "DEPOSIT_LIABILITY",
                    LedgerAccountType.DEPOSIT_LIABILITY,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(liability));
        savedAccount = new AtomicReference<>();
        when(accountRepository.save(any())).thenAnswer(invocation -> {
            DepositAccount account = invocation.getArgument(0);
            savedAccount.set(account);
            return account;
        });
        ledgerCommand = new AtomicReference<>();
        doAnswer(invocation -> {
            UUID groupId = invocation.getArgument(0);
            LedgerTransactionCommand submitted = invocation.getArgument(1);
            ledgerCommand.set(submitted);
            List<?> mutations = invocation.getArgument(2);
            mutations.forEach(value -> ((LedgerDomainMutation) value).validate());
            mutations.forEach(value -> ((LedgerDomainMutation) value).apply());
            return new LedgerTransactionResult(
                groupId,
                TransactionGroupStatus.COMMITTED,
                1,
                false
            );
        }).when(transactionService).execute(any(UUID.class), any(), anyList());
    }

    @Test
    void opensDepositWithFrozenInterestAndReplaysIdempotently() {
        DepositAccountResult first = service.subscribe(command);

        assertEquals("ACTIVE", first.status());
        assertEquals(50, first.expectedInterestAmount());
        assertEquals(NOW.plus(Duration.ofDays(30)), first.maturesAt());
        assertEquals(
            List.of(-1_000L, 1_000L),
            ledgerCommand.get().postings().stream()
                .map(posting -> posting.amount())
                .sorted()
                .toList()
        );

        IdempotencyRecord replay = IdempotencyRecord.completed(
            UUID.randomUUID(),
            command.classroomId(),
            UUID.fromString(command.studentSubject()),
            ledgerCommand.get().routeKey(),
            command.requestId().toString(),
            ledgerCommand.get().requestHash(),
            201,
            Map.of("transaction_group_id", savedAccount.get().getId()),
            savedAccount.get().getId(),
            NOW.plus(Duration.ofDays(365)),
            NOW
        );
        when(
            idempotencyRepository.findForReplay(
                eq(command.classroomId()),
                eq(UUID.fromString(command.studentSubject())),
                any(),
                eq(command.requestId().toString())
            )
        ).thenReturn(Optional.of(replay));
        when(
            accountRepository.findByClassroomIdAndOpeningTransactionGroupId(
                command.classroomId(),
                replay.getTransactionGroupId()
            )
        ).thenReturn(Optional.of(savedAccount.get()));

        DepositAccountResult repeated = service.subscribe(command);

        assertEquals(first.accountId(), repeated.accountId());
        verify(transactionService, times(1)).execute(any(UUID.class), any(), anyList());
        verify(accountRepository, times(1)).save(any());
    }
}
