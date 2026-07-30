package com.b201.ecovillage.ledger.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ledger.domain.LedgerEntry;
import com.b201.ecovillage.ledger.domain.TransactionGroup;
import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;
import com.b201.ecovillage.ledger.repository.LedgerEntryRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;

@Service
public class LedgerRecoveryService {

    private final AtomicLedgerTransactionService transactionService;
    private final TransactionGroupRepository transactionGroupRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerRecoveryService(
        AtomicLedgerTransactionService transactionService,
        TransactionGroupRepository transactionGroupRepository,
        LedgerEntryRepository ledgerEntryRepository
    ) {
        this.transactionService = transactionService;
        this.transactionGroupRepository = transactionGroupRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerRecoveryResult recover(LedgerRecoveryCommand command) {
        return recover(command, List.of());
    }

    @Transactional
    public LedgerRecoveryResult recover(
        LedgerRecoveryCommand command,
        List<? extends LedgerDomainMutation> itemOrDomainMutations
    ) {
        TransactionGroup original = transactionGroupRepository
            .findForRecovery(command.classroomId(), command.originalTransactionGroupId())
            .orElseThrow(LedgerRecoveryService::originalNotCommitted);
        if (original.getStatus() != TransactionGroupStatus.COMMITTED) {
            throw originalNotCommitted();
        }

        List<LedgerEntry> originalEntries = ledgerEntryRepository
            .findAllByTransactionGroupIdOrderByLedgerAccountId(original.getId());
        if (originalEntries.size() < 2) {
            throw new LedgerTransactionValidationException(
                "ORIGINAL_TRANSACTION_INVALID",
                "committed transaction does not contain a balanced ledger"
            );
        }

        List<LedgerTransactionPosting> reversePostings = originalEntries.stream()
            .map(LedgerRecoveryService::reverse)
            .toList();
        LedgerTransactionCommand transactionCommand = LedgerTransactionCommand.recovery(
            command.classroomId(),
            "RECOVERY_" + command.recoveryType().name(),
            command.actorType(),
            command.actorId(),
            original.getPolicySnapshotId(),
            original.getId(),
            original.getRootTransactionGroupId(),
            command.routeKey(),
            command.idempotencyKey(),
            command.requestHash(),
            command.correlationId(),
            command.reason(),
            reversePostings,
            command.occurredAt()
        );

        List<LedgerDomainMutation> mutations = new ArrayList<>();
        mutations.add(directRecoveryGuard(original.getId()));
        mutations.addAll(List.copyOf(itemOrDomainMutations));
        LedgerTransactionResult result = transactionService.execute(transactionCommand, mutations);
        return new LedgerRecoveryResult(
            result.transactionGroupId(),
            original.getId(),
            original.getRootTransactionGroupId(),
            result.status(),
            result.replayed()
        );
    }

    private LedgerDomainMutation directRecoveryGuard(java.util.UUID originalGroupId) {
        return new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (transactionGroupRepository.existsByRecoveryOfGroupId(originalGroupId)) {
                    throw new LedgerTransactionValidationException(
                        "RECOVERY_ALREADY_EXISTS",
                        "the transaction already has a direct recovery"
                    );
                }
            }

            @Override
            public void apply() {
            }
        };
    }

    private static LedgerTransactionPosting reverse(LedgerEntry entry) {
        try {
            return new LedgerTransactionPosting(
                entry.getLedgerAccountId(),
                Math.negateExact(entry.getAmount())
            );
        } catch (ArithmeticException exception) {
            throw new LedgerTransactionValidationException(
                "AMOUNT_OVERFLOW",
                "original ledger amount cannot be reversed"
            );
        }
    }

    private static LedgerTransactionValidationException originalNotCommitted() {
        return new LedgerTransactionValidationException(
            "ORIGINAL_TRANSACTION_NOT_COMMITTED",
            "only a committed transaction can be recovered"
        );
    }
}
