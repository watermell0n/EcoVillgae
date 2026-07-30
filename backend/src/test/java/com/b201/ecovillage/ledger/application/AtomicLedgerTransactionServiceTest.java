package com.b201.ecovillage.ledger.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.b201.ecovillage.ledger.domain.InsufficientLedgerBalanceException;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.LedgerEntryRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:atomic-ledger;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AtomicLedgerTransactionServiceTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-30T02:00:00Z");

    @Autowired
    private AtomicLedgerTransactionService transactionService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void commitsBalancedPostingsAsOneTransaction() {
        AccountPair accounts = createEmptyAccountPair();
        LedgerTransactionCommand command = command(
            accounts,
            "key-commit-" + UUID.randomUUID(),
            "hash-commit",
            100
        );

        LedgerTransactionResult result = transactionService.execute(command);

        assertEquals(TransactionGroupStatus.COMMITTED, result.status());
        assertFalse(result.replayed());
        assertEquals(-100, balance(accounts.systemAccountId()));
        assertEquals(100, balance(accounts.studentWalletId()));
        assertEquals(
            0,
            ledgerEntryRepository
                .findAllByTransactionGroupIdOrderByLedgerAccountId(result.transactionGroupId())
                .stream()
                .mapToLong(entry -> entry.getAmount())
                .sum()
        );
        assertEquals(
            2,
            ledgerEntryRepository
                .findAllByTransactionGroupIdOrderByLedgerAccountId(result.transactionGroupId())
                .size()
        );
    }

    @Test
    void rollsBackEveryPostingWhenStudentBalanceIsInsufficient() {
        AccountPair accounts = createEmptyAccountPair();
        String key = "key-insufficient-" + UUID.randomUUID();
        LedgerTransactionCommand command = LedgerTransactionCommand.original(
            accounts.classroomId(),
            "PURCHASE",
            LedgerActorType.STUDENT,
            accounts.actorId(),
            null,
            "/store/orders",
            key,
            "hash-insufficient",
            "correlation-insufficient",
            "구매",
            List.of(
                new LedgerTransactionPosting(accounts.studentWalletId(), -10),
                new LedgerTransactionPosting(accounts.systemAccountId(), 10)
            ),
            OCCURRED_AT
        );

        assertThrows(
            InsufficientLedgerBalanceException.class,
            () -> transactionService.execute(command)
        );

        assertEquals(0, balance(accounts.systemAccountId()));
        assertEquals(0, balance(accounts.studentWalletId()));
    }

    @Test
    void rollsBackLedgerWhenStockOrItemMutationFails() {
        AccountPair accounts = createEmptyAccountPair();
        LedgerTransactionCommand command = command(
            accounts,
            "key-mutation-" + UUID.randomUUID(),
            "hash-mutation",
            30
        );
        LedgerDomainMutation failedStockMutation = new LedgerDomainMutation() {
            @Override
            public void validate() {
            }

            @Override
            public void apply() {
                throw new IllegalStateException("stock changed concurrently");
            }
        };

        assertThrows(
            IllegalStateException.class,
            () -> transactionService.execute(command, List.of(failedStockMutation))
        );

        assertEquals(0, balance(accounts.systemAccountId()));
        assertEquals(0, balance(accounts.studentWalletId()));
    }

    @Test
    void replaysSameRequestWithoutApplyingItTwice() {
        AccountPair accounts = createEmptyAccountPair();
        String key = "key-replay-" + UUID.randomUUID();
        LedgerTransactionCommand command = command(accounts, key, "hash-replay", 70);

        LedgerTransactionResult first = transactionService.execute(command);
        LedgerTransactionResult replay = transactionService.execute(command);

        assertEquals(first.transactionGroupId(), replay.transactionGroupId());
        assertTrue(replay.replayed());
        assertEquals(-70, balance(accounts.systemAccountId()));
        assertEquals(70, balance(accounts.studentWalletId()));
    }

    @Test
    void rejectsIdempotencyKeyReusedForDifferentRequest() {
        AccountPair accounts = createEmptyAccountPair();
        String key = "key-conflict-" + UUID.randomUUID();
        transactionService.execute(command(accounts, key, "hash-first", 20));

        assertThrows(
            IdempotencyKeyReusedException.class,
            () -> transactionService.execute(command(accounts, key, "hash-second", 20))
        );
        assertEquals(20, balance(accounts.studentWalletId()));
    }

    @Test
    void rejectsUnbalancedRequestBeforeChangingAnyAccount() {
        AccountPair accounts = createEmptyAccountPair();
        LedgerTransactionCommand command = LedgerTransactionCommand.original(
            accounts.classroomId(),
            "INVALID",
            LedgerActorType.SYSTEM,
            accounts.actorId(),
            null,
            "/test/unbalanced",
            "key-unbalanced-" + UUID.randomUUID(),
            "hash-unbalanced",
            "correlation-unbalanced",
            "불균형",
            List.of(
                new LedgerTransactionPosting(accounts.systemAccountId(), -10),
                new LedgerTransactionPosting(accounts.studentWalletId(), 9)
            ),
            OCCURRED_AT
        );

        LedgerTransactionValidationException exception = assertThrows(
            LedgerTransactionValidationException.class,
            () -> transactionService.execute(command)
        );

        assertEquals("UNBALANCED_TRANSACTION", exception.getCode());
        assertEquals(0, balance(accounts.systemAccountId()));
        assertEquals(0, balance(accounts.studentWalletId()));
    }

    private AccountPair createEmptyAccountPair() {
        UUID classroomId = UUID.randomUUID();
        LedgerAccount system = LedgerAccount.systemAccount(
            UUID.randomUUID(),
            classroomId,
            "money-issuance",
            LedgerAccountType.MONEY_ISSUANCE,
            OCCURRED_AT.minusSeconds(1)
        );
        LedgerAccount wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            UUID.randomUUID(),
            OCCURRED_AT.minusSeconds(1)
        );
        ledgerAccountRepository.save(system);
        ledgerAccountRepository.save(wallet);
        return new AccountPair(classroomId, UUID.randomUUID(), system.getId(), wallet.getId());
    }

    private LedgerTransactionCommand command(
        AccountPair accounts,
        String idempotencyKey,
        String requestHash,
        long amount
    ) {
        return LedgerTransactionCommand.original(
            accounts.classroomId(),
            "QUIZ",
            LedgerActorType.TEACHER,
            accounts.actorId(),
            null,
            "/payouts",
            idempotencyKey,
            requestHash,
            "correlation-" + idempotencyKey,
            "퀴즈 보상",
            List.of(
                new LedgerTransactionPosting(accounts.systemAccountId(), -amount),
                new LedgerTransactionPosting(accounts.studentWalletId(), amount)
            ),
            OCCURRED_AT
        );
    }

    private long balance(UUID accountId) {
        return ledgerAccountRepository.findById(accountId).orElseThrow().getCurrentBalance();
    }

    private record AccountPair(
        UUID classroomId,
        UUID actorId,
        UUID systemAccountId,
        UUID studentWalletId
    ) {
    }
}
