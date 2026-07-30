package com.b201.ecovillage.trade.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
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
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.domain.InventoryItemStatus;
import com.b201.ecovillage.trade.domain.Trade;
import com.b201.ecovillage.trade.domain.TradeDecision;
import com.b201.ecovillage.trade.domain.TradeItemLeg;
import com.b201.ecovillage.trade.domain.TradeMoneyLeg;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;
import com.b201.ecovillage.trade.repository.TradeDecisionRepository;
import com.b201.ecovillage.trade.repository.TradeItemLegRepository;
import com.b201.ecovillage.trade.repository.TradeMoneyLegRepository;
import com.b201.ecovillage.trade.repository.TradeRepository;

@Service
public class TradeTeacherDecisionService {

    private static final String ROUTE_KEY =
        "/classrooms/{classroom_id}/trades/{trade_id}/teacher-decisions";
    private static final String CLASSROOM_FUND_KEY = "CLASSROOM_FUND";
    private static final int DEFAULT_TRADE_TAX_RATE_BPS = 500;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ClassroomPolicyVersionRepository policyVersionRepository;
    private final TradeRepository tradeRepository;
    private final TradeMoneyLegRepository moneyLegRepository;
    private final TradeItemLegRepository itemLegRepository;
    private final TradeDecisionRepository decisionRepository;
    private final InventoryItemRepository inventoryRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PolicySnapshotRepository snapshotRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AtomicLedgerTransactionService ledgerTransactionService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public TradeTeacherDecisionService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        ClassroomPolicyVersionRepository policyVersionRepository,
        TradeRepository tradeRepository,
        TradeMoneyLegRepository moneyLegRepository,
        TradeItemLegRepository itemLegRepository,
        TradeDecisionRepository decisionRepository,
        InventoryItemRepository inventoryRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PolicySnapshotRepository snapshotRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AtomicLedgerTransactionService ledgerTransactionService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.tradeRepository = tradeRepository;
        this.moneyLegRepository = moneyLegRepository;
        this.itemLegRepository = itemLegRepository;
        this.decisionRepository = decisionRepository;
        this.inventoryRepository = inventoryRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.snapshotRepository = snapshotRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.ledgerTransactionService = ledgerTransactionService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public TradeCommandResult decide(TeacherTradeDecisionRequest command) {
        validateCommand(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.APPROVE_TRADE,
            null
        );
        UUID teacherId = requireTeacher(command.teacherSubject()).getId();
        requireActiveClassroom(command.classroomId());
        String requestHash = requestHash(command);
        TradeCommandResult replay = replay(
            command.classroomId(),
            teacherId,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        Trade trade = tradeRepository
            .findForUpdate(command.classroomId(), command.tradeId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The trade could not be found."
            ));
        requireExpectedVersion(command.expectedStateVersion(), trade.getStateVersion());
        Instant now = clock.instant();

        if (command.decision() == TeacherTradeDecision.REJECT) {
            return reject(command, teacherId, trade, requestHash, now);
        }
        requireActiveStudents(trade);
        return approve(command, teacherId, trade, requestHash, now);
    }

    private TradeCommandResult reject(
        TeacherTradeDecisionRequest command,
        UUID teacherId,
        Trade trade,
        String requestHash,
        Instant now
    ) {
        trade.rejectByTeacher(now);
        List<InventoryItem> items = lockedItems(trade);
        items.forEach(item -> item.releaseTradeLock(trade.getId(), now));
        inventoryRepository.saveAll(items);
        decisionRepository.save(
            teacherDecision(command, teacherId, trade, now)
        );
        tradeRepository.saveAndFlush(trade);
        TradeCommandResult result = result(trade);
        saveIdempotency(command, teacherId, requestHash, result, now);
        return result;
    }

    private TradeCommandResult approve(
        TeacherTradeDecisionRequest command,
        UUID teacherId,
        Trade trade,
        String requestHash,
        Instant now
    ) {
        List<TradeMoneyLeg> moneyLegs = moneyLegRepository
            .findAllByTradeIdOrderByFromStudentId(trade.getId());
        List<TradeItemLeg> itemLegs = itemLegRepository
            .findAllByTradeIdOrderByInventoryItemId(trade.getId());
        List<InventoryItem> items = lockedItems(trade, itemLegs);
        TradeTaxPolicy taxPolicy = currentTaxPolicy(command.classroomId());
        SettlementAmounts amounts = calculateAmounts(moneyLegs, taxPolicy.rateBps());
        PolicySnapshot snapshot = snapshot(trade, taxPolicy, amounts, now);
        Map<UUID, LedgerAccount> wallets = wallets(trade, now);
        LedgerAccount fund = amounts.totalTax() == 0
            ? null
            : systemFund(command.classroomId(), now);
        List<LedgerTransactionPosting> postings = postings(amounts, wallets, fund);
        UUID transactionGroupId = postings.size() < 2 ? null : uuidGenerator.generate();

        SettlementMutation settlement = new SettlementMutation(
            command,
            teacherId,
            trade,
            moneyLegs,
            itemLegs,
            items,
            wallets,
            amounts,
            snapshot.getId(),
            transactionGroupId,
            now
        );
        if (transactionGroupId == null) {
            lockWalletsForDirectSettlement(command.classroomId(), moneyLegs, wallets);
            settlement.validate();
            settlement.apply();
            tradeRepository.flush();
            TradeCommandResult result = result(trade);
            saveIdempotency(command, teacherId, requestHash, result, now);
            return result;
        }

        ledgerTransactionService.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                command.classroomId(),
                "PERSONAL_TRADE",
                LedgerActorType.TEACHER,
                teacherId,
                snapshot.getId(),
                ROUTE_KEY,
                command.requestId().toString(),
                requestHash,
                command.correlationId().toString(),
                command.reason(),
                postings,
                now
            ),
            List.of(settlement)
        );
        tradeRepository.flush();
        return result(trade);
    }

    private final class SettlementMutation implements LedgerDomainMutation {

        private final TeacherTradeDecisionRequest command;
        private final UUID teacherId;
        private final Trade trade;
        private final List<TradeMoneyLeg> moneyLegs;
        private final List<TradeItemLeg> itemLegs;
        private final List<InventoryItem> items;
        private final Map<UUID, LedgerAccount> wallets;
        private final SettlementAmounts amounts;
        private final UUID policySnapshotId;
        private final UUID transactionGroupId;
        private final Instant now;

        private SettlementMutation(
            TeacherTradeDecisionRequest command,
            UUID teacherId,
            Trade trade,
            List<TradeMoneyLeg> moneyLegs,
            List<TradeItemLeg> itemLegs,
            List<InventoryItem> items,
            Map<UUID, LedgerAccount> wallets,
            SettlementAmounts amounts,
            UUID policySnapshotId,
            UUID transactionGroupId,
            Instant now
        ) {
            this.command = command;
            this.teacherId = teacherId;
            this.trade = trade;
            this.moneyLegs = moneyLegs;
            this.itemLegs = itemLegs;
            this.items = items;
            this.wallets = wallets;
            this.amounts = amounts;
            this.policySnapshotId = policySnapshotId;
            this.transactionGroupId = transactionGroupId;
            this.now = now;
        }

        @Override
        public void validate() {
            for (Map.Entry<UUID, Long> required : amounts.requiredDebit().entrySet()) {
                LedgerAccount wallet = wallets.get(required.getKey());
                if (wallet == null || wallet.getCurrentBalance() < required.getValue()) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "INSUFFICIENT_BALANCE",
                        "A trade student cannot cover the outgoing amount and trade tax."
                    );
                }
            }
            Map<UUID, InventoryItem> byId = new HashMap<>();
            items.forEach(item -> byId.put(item.getId(), item));
            for (TradeItemLeg leg : itemLegs) {
                InventoryItem item = byId.get(leg.getInventoryItemId());
                if (
                    item == null
                        || !item.getClassroomStudentId().equals(leg.getFromStudentId())
                        || item.getStatus() != InventoryItemStatus.LOCKED_TRADE
                        || !Objects.equals(item.getLockedTradeId(), trade.getId())
                ) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "OWNERSHIP_CHANGED",
                        "A trade item owner or lock has changed."
                    );
                }
            }
        }

        @Override
        public void apply() {
            trade.beginSettlement(policySnapshotId, transactionGroupId, now);
            Map<UUID, InventoryItem> byId = new HashMap<>();
            items.forEach(item -> byId.put(item.getId(), item));
            itemLegs.forEach(leg ->
                byId.get(leg.getInventoryItemId())
                    .transferTo(trade.getId(), leg.getToStudentId(), now)
            );
            inventoryRepository.saveAll(items);
            decisionRepository.save(teacherDecision(command, teacherId, trade, now));
            trade.complete(now);
            tradeRepository.save(trade);
        }
    }

    private SettlementAmounts calculateAmounts(
        List<TradeMoneyLeg> moneyLegs,
        int rateBps
    ) {
        Map<UUID, Long> deltas = new LinkedHashMap<>();
        Map<UUID, Long> requiredDebit = new LinkedHashMap<>();
        long totalTax = 0;
        try {
            for (TradeMoneyLeg leg : moneyLegs) {
                long tax = BigDecimal.valueOf(leg.getAmount())
                    .multiply(BigDecimal.valueOf(rateBps))
                    .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                    .longValueExact();
                long debit = Math.addExact(leg.getAmount(), tax);
                deltas.merge(
                    leg.getFromStudentId(),
                    -debit,
                    Math::addExact
                );
                deltas.merge(
                    leg.getToStudentId(),
                    leg.getAmount(),
                    Math::addExact
                );
                requiredDebit.merge(
                    leg.getFromStudentId(),
                    debit,
                    Math::addExact
                );
                totalTax = Math.addExact(totalTax, tax);
            }
        } catch (ArithmeticException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AMOUNT_OVERFLOW",
                "The trade amount or tax exceeds the supported range."
            );
        }
        return new SettlementAmounts(
            Map.copyOf(deltas),
            Map.copyOf(requiredDebit),
            totalTax
        );
    }

    private List<LedgerTransactionPosting> postings(
        SettlementAmounts amounts,
        Map<UUID, LedgerAccount> wallets,
        LedgerAccount fund
    ) {
        List<LedgerTransactionPosting> postings = new ArrayList<>();
        amounts.walletDeltas().forEach((studentId, delta) -> {
            if (delta != 0) {
                postings.add(new LedgerTransactionPosting(wallets.get(studentId).getId(), delta));
            }
        });
        if (fund != null && amounts.totalTax() != 0) {
            postings.add(new LedgerTransactionPosting(fund.getId(), amounts.totalTax()));
        }
        return postings;
    }

    private Map<UUID, LedgerAccount> wallets(Trade trade, Instant now) {
        Map<UUID, LedgerAccount> wallets = new LinkedHashMap<>();
        wallets.put(
            trade.getProposerStudentId(),
            wallet(trade.getClassroomId(), trade.getProposerStudentId(), now)
        );
        wallets.put(
            trade.getCounterpartStudentId(),
            wallet(trade.getClassroomId(), trade.getCounterpartStudentId(), now)
        );
        return wallets;
    }

    private LedgerAccount wallet(UUID classroomId, UUID studentId, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.studentWallet(
                    uuidGenerator.generate(),
                    classroomId,
                    studentId,
                    now
                )
            ));
    }

    private LedgerAccount systemFund(UUID classroomId, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                CLASSROOM_FUND_KEY,
                LedgerAccountType.CLASSROOM_FUND,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.systemAccount(
                    uuidGenerator.generate(),
                    classroomId,
                    CLASSROOM_FUND_KEY,
                    LedgerAccountType.CLASSROOM_FUND,
                    now
                )
            ));
    }

    private void lockWalletsForDirectSettlement(
        UUID classroomId,
        List<TradeMoneyLeg> moneyLegs,
        Map<UUID, LedgerAccount> wallets
    ) {
        if (moneyLegs.isEmpty()) {
            return;
        }
        List<UUID> walletIds = wallets.values().stream().map(LedgerAccount::getId).toList();
        List<LedgerAccount> locked = ledgerAccountRepository.findAllForUpdate(
            classroomId,
            walletIds
        );
        if (locked.size() != walletIds.size()) {
            throw operation(
                HttpStatus.CONFLICT,
                "LEDGER_ACCOUNT_NOT_FOUND",
                "A trade wallet could not be locked."
            );
        }
        locked.forEach(account ->
            wallets.put(account.getClassroomStudentId(), account)
        );
    }

    private List<InventoryItem> lockedItems(Trade trade) {
        return lockedItems(
            trade,
            itemLegRepository.findAllByTradeIdOrderByInventoryItemId(trade.getId())
        );
    }

    private List<InventoryItem> lockedItems(
        Trade trade,
        List<TradeItemLeg> itemLegs
    ) {
        if (itemLegs.isEmpty()) {
            return List.of();
        }
        List<UUID> itemIds = itemLegs.stream()
            .map(TradeItemLeg::getInventoryItemId)
            .toList();
        List<InventoryItem> items = inventoryRepository.findAllForUpdate(
            trade.getClassroomId(),
            itemIds
        );
        if (items.size() != itemIds.size()) {
            throw operation(
                HttpStatus.CONFLICT,
                "OWNERSHIP_CHANGED",
                "A locked trade item is missing."
            );
        }
        return items;
    }

    private TradeTaxPolicy currentTaxPolicy(UUID classroomId) {
        Optional<ClassroomPolicyVersion> current = policyVersionRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                classroomId,
                "ECONOMIC"
            )
            .or(() -> policyVersionRepository
                .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                    classroomId,
                    "SALARY"
                ));
        if (current.isEmpty()) {
            return new TradeTaxPolicy(
                DEFAULT_TRADE_TAX_RATE_BPS,
                null,
                0,
                "SYSTEM_DEFAULT"
            );
        }
        ClassroomPolicyVersion policy = current.get();
        Object configured = policy.getPolicyValues().get("trade_tax_rate_bps");
        int rate = configured == null
            ? DEFAULT_TRADE_TAX_RATE_BPS
            : rateBps(configured);
        return new TradeTaxPolicy(
            rate,
            policy.getId(),
            policy.getVersion(),
            policy.getPolicyArea()
        );
    }

    private PolicySnapshot snapshot(
        Trade trade,
        TradeTaxPolicy policy,
        SettlementAmounts amounts,
        Instant now
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("trade_id", trade.getId());
        values.put("trade_tax_rate_bps", policy.rateBps());
        values.put("total_trade_tax", amounts.totalTax());
        values.put("policy_source", policy.source());
        values.put("policy_version", policy.version());
        if (policy.policyVersionId() != null) {
            values.put("policy_version_id", policy.policyVersionId());
        }
        String hash = sha256(
            trade.getClassroomId() + "|" + trade.getId() + "|" + policy.rateBps()
                + "|" + policy.policyVersionId() + "|" + amounts.totalTax()
        );
        return snapshotRepository
            .findByClassroomIdAndSnapshotHash(trade.getClassroomId(), hash)
            .orElseGet(() -> snapshotRepository.save(
                PolicySnapshot.capture(
                    uuidGenerator.generate(),
                    trade.getClassroomId(),
                    hash,
                    "1",
                    values,
                    "HALF_UP",
                    now
                )
            ));
    }

    private TradeCommandResult replay(
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
        Trade trade;
        if (record.getTransactionGroupId() != null) {
            trade = tradeRepository
                .findByClassroomIdAndTransactionGroupId(
                    classroomId,
                    record.getTransactionGroupId()
                )
                .orElseThrow(() -> missingReplay());
        } else {
            UUID tradeId = responseUuid(record.getResponseBody().get("trade_id"));
            trade = tradeRepository
                .findByClassroomIdAndId(classroomId, tradeId)
                .orElseThrow(() -> missingReplay());
        }
        return result(trade);
    }

    private void saveIdempotency(
        TeacherTradeDecisionRequest command,
        UUID teacherId,
        String requestHash,
        TradeCommandResult result,
        Instant now
    ) {
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                command.classroomId(),
                teacherId,
                ROUTE_KEY,
                command.requestId().toString(),
                requestHash,
                HttpStatus.OK.value(),
                Map.of(
                    "trade_id", result.tradeId(),
                    "status", result.status(),
                    "state_version", result.stateVersion()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private TradeDecision teacherDecision(
        TeacherTradeDecisionRequest command,
        UUID teacherId,
        Trade trade,
        Instant now
    ) {
        return TradeDecision.record(
            uuidGenerator.generate(),
            command.classroomId(),
            trade.getId(),
            "TEACHER",
            "TEACHER",
            teacherId,
            command.decision().name(),
            command.reason(),
            now
        );
    }

    private void requireActiveStudents(Trade trade) {
        List<ClassroomStudent> students = studentRepository.findAllForUpdate(
            trade.getClassroomId(),
            List.of(trade.getProposerStudentId(), trade.getCounterpartStudentId()),
            ClassroomStudentStatus.ACTIVE
        );
        if (students.size() != 2) {
            throw operation(
                HttpStatus.CONFLICT,
                "STUDENT_NOT_AVAILABLE",
                "Both trade students must still be active."
            );
        }
    }

    private UserAccount requireTeacher(String subject) {
        return userRepository.findByExternalSubject(subject)
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
    }

    private void requireActiveClassroom(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Trade decisions are available only in an active classroom."
            );
        }
    }

    private static int rateBps(Object value) {
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw policyViolation();
            }
        }
        if (parsed < 0 || parsed > 10_000) {
            throw policyViolation();
        }
        return Math.toIntExact(parsed);
    }

    private static void validateCommand(TeacherTradeDecisionRequest command) {
        if (
            command == null
                || command.classroomId() == null
                || command.tradeId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.decision() == null
                || command.reason() == null
                || command.reason().isBlank()
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the teacher trade decision values."
            );
        }
    }

    private static void requireExpectedVersion(Long expected, long current) {
        if (expected != null && expected != current) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The trade state has changed. Refresh and try again."
            );
        }
    }

    private static String requestHash(TeacherTradeDecisionRequest command) {
        return sha256(
            command.classroomId() + "|" + command.tradeId() + "|" + command.decision()
                + "|" + command.reason().trim() + "|" + command.expectedStateVersion()
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
            throw new IllegalStateException("Could not hash the trade decision.", exception);
        }
    }

    private static UUID responseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw missingReplay();
        }
    }

    private static ClassroomOperationException missingReplay() {
        return operation(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_RESULT_MISSING",
            "The previous trade decision result could not be found."
        );
    }

    private static ClassroomOperationException policyViolation() {
        return operation(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "POLICY_VIOLATION",
            "The personal trade tax rate must be between 0 and 10000 bps."
        );
    }

    private static TradeCommandResult result(Trade trade) {
        return new TradeCommandResult(
            trade.getId(),
            trade.getStatus().name(),
            trade.getStateVersion()
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private record TradeTaxPolicy(
        int rateBps,
        UUID policyVersionId,
        int version,
        String source
    ) {
    }

    private record SettlementAmounts(
        Map<UUID, Long> walletDeltas,
        Map<UUID, Long> requiredDebit,
        long totalTax
    ) {
    }
}
