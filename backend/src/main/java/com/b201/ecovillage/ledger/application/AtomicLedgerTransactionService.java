package com.b201.ecovillage.ledger.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountPosting;
import com.b201.ecovillage.ledger.domain.LedgerEntry;
import com.b201.ecovillage.ledger.domain.TransactionGroup;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.LedgerEntryRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;

@Service
public class AtomicLedgerTransactionService {

    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(365);

    private final UuidV7Generator uuidGenerator;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionGroupRepository transactionGroupRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public AtomicLedgerTransactionService(
        UuidV7Generator uuidGenerator,
        LedgerAccountRepository ledgerAccountRepository,
        LedgerEntryRepository ledgerEntryRepository,
        TransactionGroupRepository transactionGroupRepository,
        IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        this.uuidGenerator = uuidGenerator;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionGroupRepository = transactionGroupRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public LedgerTransactionResult execute(LedgerTransactionCommand command) {
        return execute(command, List.of());
    }

    @Transactional
    public LedgerTransactionResult execute(
        LedgerTransactionCommand command,
        List<? extends LedgerDomainMutation> domainMutations
    ) {
        return execute(null, command, domainMutations);
    }

    @Transactional
    public LedgerTransactionResult execute(
        UUID requestedTransactionGroupId,
        LedgerTransactionCommand command,
        List<? extends LedgerDomainMutation> domainMutations
    ) {
        List<? extends LedgerDomainMutation> mutations = List.copyOf(domainMutations);
        IdempotencyRecord replay = idempotencyRecordRepository
            .findForReplay(
                command.classroomId(),
                command.actorId(),
                command.routeKey(),
                command.idempotencyKey()
            )
            .orElse(null);
        if (replay != null) {
            return replay(command, replay);
        }

        List<LedgerTransactionPosting> postings = validatedPostings(command.postings());
        Map<UUID, LedgerAccount> accounts = lockedAccounts(command.classroomId(), postings);

        replay = idempotencyRecordRepository
            .findForReplay(
                command.classroomId(),
                command.actorId(),
                command.routeKey(),
                command.idempotencyKey()
            )
            .orElse(null);
        if (replay != null) {
            return replay(command, replay);
        }

        mutations.forEach(LedgerDomainMutation::validate);

        UUID groupId = requestedTransactionGroupId == null
            ? uuidGenerator.generate()
            : requestedTransactionGroupId;
        TransactionGroup group = createTransactionGroup(command, groupId);
        group.startValidation(command.occurredAt());
        transactionGroupRepository.saveAndFlush(group);

        List<LedgerEntry> entries = new ArrayList<>(postings.size());
        for (LedgerTransactionPosting posting : postings) {
            LedgerAccount account = accounts.get(posting.ledgerAccountId());
            LedgerAccountPosting accountPosting = account.post(
                posting.amount(),
                command.occurredAt()
            );
            entries.add(
                LedgerEntry.post(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    groupId,
                    account.getId(),
                    accountPosting,
                    command.occurredAt()
                )
            );
        }

        mutations.forEach(LedgerDomainMutation::apply);
        ledgerEntryRepository.saveAll(entries);
        group.commit(command.occurredAt());
        transactionGroupRepository.save(group);

        LedgerTransactionResult result = new LedgerTransactionResult(
            groupId,
            group.getStatus(),
            group.getStateVersion(),
            false
        );
        idempotencyRecordRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                command.classroomId(),
                command.actorId(),
                command.routeKey(),
                command.idempotencyKey(),
                command.requestHash(),
                201,
                responseBody(result),
                groupId,
                command.occurredAt().plus(IDEMPOTENCY_RETENTION),
                command.occurredAt()
            )
        );
        return result;
    }

    private LedgerTransactionResult replay(
        LedgerTransactionCommand command,
        IdempotencyRecord record
    ) {
        if (!record.hasRequestHash(command.requestHash())) {
            throw new IdempotencyKeyReusedException();
        }
        TransactionGroup existing = transactionGroupRepository
            .findById(record.getTransactionGroupId())
            .orElseThrow(
                () -> new LedgerTransactionValidationException(
                    "IDEMPOTENCY_RESULT_MISSING",
                    "stored idempotency result has no transaction group"
                )
            );
        return new LedgerTransactionResult(
            existing.getId(),
            existing.getStatus(),
            existing.getStateVersion(),
            true
        );
    }

    private TransactionGroup createTransactionGroup(
        LedgerTransactionCommand command,
        UUID groupId
    ) {
        if (command.isRecovery()) {
            return TransactionGroup.receiveRecovery(
                groupId,
                command.classroomId(),
                command.transactionType(),
                command.actorType(),
                command.actorId(),
                command.policySnapshotId(),
                command.recoveryOfGroupId(),
                command.rootTransactionGroupId(),
                command.idempotencyKey(),
                command.correlationId(),
                command.reason(),
                command.occurredAt()
            );
        }
        return TransactionGroup.receive(
            groupId,
            command.classroomId(),
            command.transactionType(),
            command.actorType(),
            command.actorId(),
            command.policySnapshotId(),
            command.idempotencyKey(),
            command.correlationId(),
            command.reason(),
            command.occurredAt()
        );
    }

    private Map<UUID, LedgerAccount> lockedAccounts(
        UUID classroomId,
        List<LedgerTransactionPosting> postings
    ) {
        List<UUID> accountIds = postings.stream()
            .map(LedgerTransactionPosting::ledgerAccountId)
            .toList();
        List<LedgerAccount> accounts = ledgerAccountRepository.findAllForUpdate(
            classroomId,
            accountIds
        );
        if (accounts.size() != accountIds.size()) {
            throw new LedgerTransactionValidationException(
                "LEDGER_ACCOUNT_NOT_FOUND",
                "one or more ledger accounts do not exist in the classroom"
            );
        }
        Map<UUID, LedgerAccount> byId = new LinkedHashMap<>();
        accounts.forEach(account -> byId.put(account.getId(), account));
        return byId;
    }

    private static List<LedgerTransactionPosting> validatedPostings(
        List<LedgerTransactionPosting> requestedPostings
    ) {
        if (requestedPostings.size() < 2) {
            throw new LedgerTransactionValidationException(
                "UNBALANCED_TRANSACTION",
                "ledger transaction requires at least two postings"
            );
        }

        long sum = 0;
        Map<UUID, LedgerTransactionPosting> unique = new LinkedHashMap<>();
        for (LedgerTransactionPosting posting : requestedPostings) {
            if (unique.putIfAbsent(posting.ledgerAccountId(), posting) != null) {
                throw new LedgerTransactionValidationException(
                    "DUPLICATE_LEDGER_ACCOUNT",
                    "a ledger account can be posted only once per transaction"
                );
            }
            try {
                sum = Math.addExact(sum, posting.amount());
            } catch (ArithmeticException exception) {
                throw new LedgerTransactionValidationException(
                    "AMOUNT_OVERFLOW",
                    "ledger posting sum exceeds the supported amount range"
                );
            }
        }
        if (sum != 0) {
            throw new LedgerTransactionValidationException(
                "UNBALANCED_TRANSACTION",
                "ledger posting amounts must sum to zero"
            );
        }
        return unique.values().stream()
            .sorted(Comparator.comparing(LedgerTransactionPosting::ledgerAccountId))
            .toList();
    }

    private static Map<String, Object> responseBody(LedgerTransactionResult result) {
        return Map.of(
            "transaction_group_id",
            result.transactionGroupId().toString(),
            "status",
            result.status().name(),
            "state_version",
            result.stateVersion()
        );
    }
}
