package com.b201.ecovillage.ledger.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.LedgerEntry;
import com.b201.ecovillage.ledger.domain.TransactionGroup;
import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.LedgerEntryRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ledger-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LedgerRecoveryServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-07-30T03:00:00Z");

    @Autowired
    private AtomicLedgerTransactionService transactionService;

    @Autowired
    private LedgerRecoveryService recoveryService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionGroupRepository transactionGroupRepository;

    @Test
    void createsOppositeEntriesWithoutChangingOriginalTransaction() {
        Fixture fixture = createOriginalTransaction(100);
        Map<UUID, Long> originalAmounts = amounts(fixture.originalGroupId());

        LedgerRecoveryResult recovery = recoveryService.recover(
            recoveryCommand(fixture, fixture.originalGroupId(), "recovery-1")
        );

        assertEquals(TransactionGroupStatus.COMMITTED, recovery.status());
        assertEquals(fixture.originalGroupId(), recovery.recoveryOfGroupId());
        assertEquals(fixture.originalGroupId(), recovery.rootTransactionGroupId());
        assertEquals(0, balance(fixture.systemAccountId()));
        assertEquals(0, balance(fixture.studentWalletId()));
        assertEquals(originalAmounts, amounts(fixture.originalGroupId()));
        amounts(recovery.transactionGroupId()).forEach(
            (accountId, recoveryAmount) ->
                assertEquals(-originalAmounts.get(accountId), recoveryAmount)
        );
        assertEquals(
            TransactionGroupStatus.COMMITTED,
            transactionGroupRepository.findById(fixture.originalGroupId()).orElseThrow().getStatus()
        );
    }

    @Test
    void replaysSameRecoveryButRejectsAnotherDirectRecovery() {
        Fixture fixture = createOriginalTransaction(40);
        LedgerRecoveryCommand command = recoveryCommand(
            fixture,
            fixture.originalGroupId(),
            "recovery-replay"
        );

        LedgerRecoveryResult first = recoveryService.recover(command);
        LedgerRecoveryResult replay = recoveryService.recover(command);

        assertEquals(first.transactionGroupId(), replay.transactionGroupId());
        assertTrue(replay.replayed());
        LedgerTransactionValidationException exception = assertThrows(
            LedgerTransactionValidationException.class,
            () -> recoveryService.recover(
                recoveryCommand(fixture, fixture.originalGroupId(), "recovery-duplicate")
            )
        );
        assertEquals("RECOVERY_ALREADY_EXISTS", exception.getCode());
        assertEquals(0, balance(fixture.studentWalletId()));
    }

    @Test
    void recoveryOfRecoveryPreservesFirstRootTransaction() {
        Fixture fixture = createOriginalTransaction(55);
        LedgerRecoveryResult firstRecovery = recoveryService.recover(
            recoveryCommand(fixture, fixture.originalGroupId(), "recovery-first")
        );

        LedgerRecoveryResult secondRecovery = recoveryService.recover(
            recoveryCommand(fixture, firstRecovery.transactionGroupId(), "recovery-second")
        );

        assertEquals(firstRecovery.transactionGroupId(), secondRecovery.recoveryOfGroupId());
        assertEquals(fixture.originalGroupId(), secondRecovery.rootTransactionGroupId());
        assertEquals(55, balance(fixture.studentWalletId()));
        List<TransactionGroup> directRecoveries =
            transactionGroupRepository.findAllByRecoveryOfGroupIdOrderByCreatedAt(
                fixture.originalGroupId()
            );
        assertEquals(1, directRecoveries.size());
        assertEquals(firstRecovery.transactionGroupId(), directRecoveries.getFirst().getId());
    }

    @Test
    void rejectsTransactionThatIsNotCommitted() {
        UUID classroomId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        TransactionGroup validating = TransactionGroup.receive(
            groupId,
            classroomId,
            "SALARY",
            LedgerActorType.TEACHER,
            UUID.randomUUID(),
            null,
            "request-validating",
            "correlation-validating",
            "처리 중",
            BASE_TIME
        );
        validating.startValidation(BASE_TIME);
        transactionGroupRepository.save(validating);
        Fixture fixture = new Fixture(
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            groupId
        );

        LedgerTransactionValidationException exception = assertThrows(
            LedgerTransactionValidationException.class,
            () -> recoveryService.recover(
                recoveryCommand(fixture, groupId, "recovery-invalid")
            )
        );

        assertEquals("ORIGINAL_TRANSACTION_NOT_COMMITTED", exception.getCode());
    }

    private Fixture createOriginalTransaction(long amount) {
        UUID classroomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LedgerAccount system = LedgerAccount.systemAccount(
            UUID.randomUUID(),
            classroomId,
            "money-issuance",
            LedgerAccountType.MONEY_ISSUANCE,
            BASE_TIME.minusSeconds(1)
        );
        LedgerAccount wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            UUID.randomUUID(),
            BASE_TIME.minusSeconds(1)
        );
        ledgerAccountRepository.save(system);
        ledgerAccountRepository.save(wallet);

        LedgerTransactionResult original = transactionService.execute(
            LedgerTransactionCommand.original(
                classroomId,
                "QUIZ",
                LedgerActorType.TEACHER,
                actorId,
                null,
                "/payouts",
                "original-" + UUID.randomUUID(),
                "original-hash-" + UUID.randomUUID(),
                "original-correlation-" + UUID.randomUUID(),
                "퀴즈 보상",
                List.of(
                    new LedgerTransactionPosting(system.getId(), -amount),
                    new LedgerTransactionPosting(wallet.getId(), amount)
                ),
                BASE_TIME
            )
        );
        return new Fixture(
            classroomId,
            actorId,
            system.getId(),
            wallet.getId(),
            original.transactionGroupId()
        );
    }

    private LedgerRecoveryCommand recoveryCommand(
        Fixture fixture,
        UUID targetGroupId,
        String requestSuffix
    ) {
        return new LedgerRecoveryCommand(
            fixture.classroomId(),
            targetGroupId,
            LedgerRecoveryType.AMOUNT_CORRECTION,
            LedgerActorType.TEACHER,
            fixture.actorId(),
            "/ledger-transactions/" + targetGroupId + "/recoveries",
            "key-" + requestSuffix,
            "hash-" + requestSuffix,
            "correlation-" + requestSuffix,
            "운영 오류 정정",
            BASE_TIME.plusSeconds(1)
        );
    }

    private Map<UUID, Long> amounts(UUID transactionGroupId) {
        return ledgerEntryRepository
            .findAllByTransactionGroupIdOrderByLedgerAccountId(transactionGroupId)
            .stream()
            .collect(Collectors.toMap(LedgerEntry::getLedgerAccountId, LedgerEntry::getAmount));
    }

    private long balance(UUID accountId) {
        return ledgerAccountRepository.findById(accountId).orElseThrow().getCurrentBalance();
    }

    private record Fixture(
        UUID classroomId,
        UUID actorId,
        UUID systemAccountId,
        UUID studentWalletId,
        UUID originalGroupId
    ) {
    }
}
