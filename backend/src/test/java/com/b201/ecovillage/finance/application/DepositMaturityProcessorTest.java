package com.b201.ecovillage.finance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.DepositAccountStatus;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionResult;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;

class DepositMaturityProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private DepositAccountRepository accountRepository;
    private AtomicLedgerTransactionService transactionService;
    private DepositMaturityProcessor processor;
    private DepositAccount account;
    private UUID classroomId;
    private AtomicReference<LedgerTransactionCommand> ledgerCommand;

    @BeforeEach
    void setUp() {
        accountRepository = mock(DepositAccountRepository.class);
        LedgerAccountRepository ledgerRepository = mock(LedgerAccountRepository.class);
        transactionService = mock(AtomicLedgerTransactionService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        processor = new DepositMaturityProcessor(
            accountRepository,
            ledgerRepository,
            transactionService,
            uuidGenerator
        );

        classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        account = DepositAccount.open(
            UUID.randomUUID(),
            classroomId,
            studentId,
            UUID.randomUUID(),
            1_000,
            50,
            NOW.minusSeconds(100),
            NOW.minusSeconds(1),
            UUID.randomUUID()
        );
        LedgerAccount wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            studentId,
            NOW.minusSeconds(100)
        );
        LedgerAccount liability = LedgerAccount.systemAccount(
            UUID.randomUUID(),
            classroomId,
            "DEPOSIT_LIABILITY",
            LedgerAccountType.DEPOSIT_LIABILITY,
            NOW.minusSeconds(100)
        );
        liability.post(1_000, NOW.minusSeconds(90));
        LedgerAccount issuance = LedgerAccount.systemAccount(
            UUID.randomUUID(),
            classroomId,
            "MONEY_ISSUANCE",
            LedgerAccountType.MONEY_ISSUANCE,
            NOW.minusSeconds(100)
        );

        when(accountRepository.findForUpdate(classroomId, account.getId()))
            .thenReturn(Optional.of(account));
        when(
            ledgerRepository
                .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                    classroomId,
                    studentId,
                    LedgerAccountType.STUDENT_WALLET,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(wallet));
        when(
            ledgerRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId,
                    "DEPOSIT_LIABILITY",
                    LedgerAccountType.DEPOSIT_LIABILITY,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(liability));
        when(
            ledgerRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId,
                    "MONEY_ISSUANCE",
                    LedgerAccountType.MONEY_ISSUANCE,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(issuance));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
    void settlesPrincipalAndInterestOnlyOnce() {
        assertTrue(processor.mature(classroomId, account.getId(), NOW));

        assertEquals(DepositAccountStatus.MATURED, account.getStatus());
        assertEquals(
            List.of(-1_000L, -50L, 1_050L),
            ledgerCommand.get().postings().stream()
                .map(posting -> posting.amount())
                .sorted()
                .toList()
        );

        assertFalse(processor.mature(classroomId, account.getId(), NOW.plusSeconds(1)));
        verify(transactionService, times(1)).execute(any(UUID.class), any(), anyList());
    }
}
