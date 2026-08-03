package com.b201.ecovillage.trade.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.character.repository.CharacterEquipmentRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.trade.domain.InventoryItem;
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
public class TradeCommandService {

    private static final String CREATE_ROUTE = "/classrooms/{classroom_id}/trades";
    private static final String PROPOSER_ROUTE =
        "/classrooms/{classroom_id}/trades/{trade_id}/proposer-commands";
    private static final String COUNTERPART_ROUTE =
        "/classrooms/{classroom_id}/trades/{trade_id}/counterpart-decisions";
    private static final Duration PROPOSAL_TTL = Duration.ofHours(24);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final TradeRepository tradeRepository;
    private final TradeMoneyLegRepository moneyLegRepository;
    private final TradeItemLegRepository itemLegRepository;
    private final TradeDecisionRepository decisionRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final CharacterEquipmentRepository characterEquipmentRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public TradeCommandService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        TradeRepository tradeRepository,
        TradeMoneyLegRepository moneyLegRepository,
        TradeItemLegRepository itemLegRepository,
        TradeDecisionRepository decisionRepository,
        InventoryItemRepository inventoryItemRepository,
        CharacterEquipmentRepository characterEquipmentRepository,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.tradeRepository = tradeRepository;
        this.moneyLegRepository = moneyLegRepository;
        this.itemLegRepository = itemLegRepository;
        this.decisionRepository = decisionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.characterEquipmentRepository = characterEquipmentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public TradeCommandResult create(CreateTradeCommand command) {
        validateCreate(command);
        UUID proposerId = parseStudentId(command.studentSubject());
        if (proposerId.equals(command.counterpartStudentId())) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "SELF_TRADE_FORBIDDEN",
                "A student cannot create a trade with themselves."
            );
        }
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.PROPOSE_TRADE,
            proposerId
        );
        Classroom classroom = requireActiveClassroom(command.classroomId());
        requireExpectedVersion(command.expectedStateVersion(), classroom.getStateVersion());
        requireActiveStudents(
            command.classroomId(),
            List.of(proposerId, command.counterpartStudentId())
        );

        String requestHash = hashCreate(command);
        TradeCommandResult replay = replay(
            command.classroomId(),
            proposerId,
            CREATE_ROUTE,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        Instant now = clock.instant();
        UUID tradeId = uuidGenerator.generate();
        Trade trade = Trade.propose(
            tradeId,
            command.classroomId(),
            proposerId,
            command.counterpartStudentId(),
            command.description(),
            now.plus(PROPOSAL_TTL),
            now
        );
        tradeRepository.saveAndFlush(trade);

        Map<UUID, InventoryItem> items = lockItems(
            tradeId,
            command.classroomId(),
            proposerId,
            command.counterpartStudentId(),
            command.offeredItemIds(),
            command.requestedItemIds(),
            now
        );
        inventoryItemRepository.saveAll(items.values());
        saveLegs(command, tradeId, proposerId, now);
        tradeRepository.flush();

        TradeCommandResult result = result(trade);
        saveIdempotency(
            command.classroomId(),
            proposerId,
            CREATE_ROUTE,
            command.requestId(),
            requestHash,
            HttpStatus.CREATED.value(),
            result,
            now
        );
        return result;
    }

    @Transactional
    public TradeCommandResult commandByProposer(ProposerTradeCommandRequest command) {
        validateProposerCommand(command);
        UUID proposerId = parseStudentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.PROPOSE_TRADE,
            proposerId
        );
        requireActiveClassroom(command.classroomId());
        requireActiveStudent(command.classroomId(), proposerId);

        String requestHash = sha256(
            command.classroomId() + "|" + command.tradeId() + "|" + command.command()
                + "|" + command.expectedStateVersion()
        );
        TradeCommandResult replay = replay(
            command.classroomId(),
            proposerId,
            PROPOSER_ROUTE,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        Trade trade = requireTradeForUpdate(command.classroomId(), command.tradeId());
        requireExpectedVersion(command.expectedStateVersion(), trade.getStateVersion());
        Instant now = clock.instant();
        trade.cancelByProposer(proposerId, now);
        releaseItemLocks(trade, now);
        decisionRepository.save(
            TradeDecision.record(
                uuidGenerator.generate(),
                command.classroomId(),
                trade.getId(),
                "PROPOSER_CANCEL",
                "STUDENT",
                proposerId,
                command.command().name(),
                null,
                now
            )
        );
        tradeRepository.saveAndFlush(trade);

        TradeCommandResult result = result(trade);
        saveIdempotency(
            command.classroomId(),
            proposerId,
            PROPOSER_ROUTE,
            command.requestId(),
            requestHash,
            HttpStatus.OK.value(),
            result,
            now
        );
        return result;
    }

    @Transactional
    public TradeCommandResult decideByCounterpart(CounterpartTradeDecisionRequest command) {
        validateCounterpartDecision(command);
        UUID counterpartId = parseStudentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.DECIDE_TRADE,
            counterpartId
        );
        requireActiveClassroom(command.classroomId());
        requireActiveStudent(command.classroomId(), counterpartId);

        String requestHash = sha256(
            command.classroomId() + "|" + command.tradeId() + "|" + command.decision()
                + "|" + normalized(command.reason()) + "|" + command.expectedStateVersion()
        );
        TradeCommandResult replay = replay(
            command.classroomId(),
            counterpartId,
            COUNTERPART_ROUTE,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        Trade trade = requireTradeForUpdate(command.classroomId(), command.tradeId());
        requireExpectedVersion(command.expectedStateVersion(), trade.getStateVersion());
        Instant now = clock.instant();
        if (command.decision() == CounterpartTradeDecision.ACCEPT) {
            trade.acceptByCounterpart(counterpartId, now);
        } else {
            trade.rejectByCounterpart(counterpartId, now);
            releaseItemLocks(trade, now);
        }
        decisionRepository.save(
            TradeDecision.record(
                uuidGenerator.generate(),
                command.classroomId(),
                trade.getId(),
                "COUNTERPART",
                "STUDENT",
                counterpartId,
                command.decision().name(),
                command.reason(),
                now
            )
        );
        tradeRepository.saveAndFlush(trade);

        TradeCommandResult result = result(trade);
        saveIdempotency(
            command.classroomId(),
            counterpartId,
            COUNTERPART_ROUTE,
            command.requestId(),
            requestHash,
            HttpStatus.OK.value(),
            result,
            now
        );
        return result;
    }

    private Map<UUID, InventoryItem> lockItems(
        UUID tradeId,
        UUID classroomId,
        UUID proposerId,
        UUID counterpartId,
        List<UUID> offeredItemIds,
        List<UUID> requestedItemIds,
        Instant now
    ) {
        List<UUID> allIds = new ArrayList<>(offeredItemIds);
        allIds.addAll(requestedItemIds);
        if (allIds.isEmpty()) {
            return Map.of();
        }
        List<InventoryItem> found = inventoryItemRepository.findAllForUpdate(
            classroomId,
            allIds
        );
        if (found.size() != allIds.size()) {
            throw operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "One or more trade items could not be found."
            );
        }
        if (characterEquipmentRepository.existsByClassroomIdAndInventoryItemIdIn(
            classroomId,
            allIds
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "ITEM_LOCKED",
                "Equipped items must be unequipped before they can be traded."
            );
        }
        Map<UUID, InventoryItem> byId = new HashMap<>();
        found.forEach(item -> byId.put(item.getId(), item));
        offeredItemIds.forEach(id -> byId.get(id).lockForTrade(tradeId, proposerId, now));
        requestedItemIds.forEach(id ->
            byId.get(id).lockForTrade(tradeId, counterpartId, now)
        );
        return byId;
    }

    private void saveLegs(
        CreateTradeCommand command,
        UUID tradeId,
        UUID proposerId,
        Instant now
    ) {
        UUID counterpartId = command.counterpartStudentId();
        if (command.offeredAmount() > 0) {
            moneyLegRepository.save(
                TradeMoneyLeg.create(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    tradeId,
                    proposerId,
                    counterpartId,
                    command.offeredAmount(),
                    now
                )
            );
        }
        if (command.requestedAmount() > 0) {
            moneyLegRepository.save(
                TradeMoneyLeg.create(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    tradeId,
                    counterpartId,
                    proposerId,
                    command.requestedAmount(),
                    now
                )
            );
        }
        command.offeredItemIds().forEach(itemId ->
            itemLegRepository.save(
                TradeItemLeg.create(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    tradeId,
                    itemId,
                    proposerId,
                    counterpartId,
                    now
                )
            )
        );
        command.requestedItemIds().forEach(itemId ->
            itemLegRepository.save(
                TradeItemLeg.create(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    tradeId,
                    itemId,
                    counterpartId,
                    proposerId,
                    now
                )
            )
        );
    }

    private void releaseItemLocks(Trade trade, Instant now) {
        List<TradeItemLeg> legs = itemLegRepository
            .findAllByTradeIdOrderByInventoryItemId(trade.getId());
        if (legs.isEmpty()) {
            return;
        }
        List<UUID> itemIds = legs.stream().map(TradeItemLeg::getInventoryItemId).toList();
        List<InventoryItem> items = inventoryItemRepository.findAllForUpdate(
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
        items.forEach(item -> item.releaseTradeLock(trade.getId(), now));
        inventoryItemRepository.saveAll(items);
    }

    private TradeCommandResult replay(
        UUID classroomId,
        UUID actorId,
        String route,
        UUID requestId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(classroomId, actorId, route, requestId.toString())
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        UUID tradeId = responseUuid(record.getResponseBody().get("trade_id"));
        Trade trade = tradeRepository
            .findByClassroomIdAndId(classroomId, tradeId)
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous trade result could not be found."
            ));
        return result(trade);
    }

    private void saveIdempotency(
        UUID classroomId,
        UUID actorId,
        String route,
        UUID requestId,
        String requestHash,
        int responseStatus,
        TradeCommandResult result,
        Instant now
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("trade_id", result.tradeId());
        response.put("status", result.status());
        response.put("state_version", result.stateVersion());
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                classroomId,
                actorId,
                route,
                requestId.toString(),
                requestHash,
                responseStatus,
                response,
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private Classroom requireActiveClassroom(UUID classroomId) {
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
                "Personal trades are available only in an active classroom."
            );
        }
        return classroom;
    }

    private void requireActiveStudents(UUID classroomId, List<UUID> studentIds) {
        List<ClassroomStudent> students = studentRepository.findAllForUpdate(
            classroomId,
            studentIds,
            ClassroomStudentStatus.ACTIVE
        );
        if (students.size() != studentIds.size()) {
            throw operation(
                HttpStatus.NOT_FOUND,
                "STUDENT_NOT_AVAILABLE",
                "Both trade students must be active members of the classroom."
            );
        }
    }

    private void requireActiveStudent(UUID classroomId, UUID studentId) {
        ClassroomStudent student = studentRepository.findById(studentId)
            .filter(candidate ->
                candidate.getClassroomId().equals(classroomId)
                    && candidate.getStatus() == ClassroomStudentStatus.ACTIVE
            )
            .orElseThrow(() -> operation(
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED",
                "The student is not active in this classroom."
            ));
    }

    private Trade requireTradeForUpdate(UUID classroomId, UUID tradeId) {
        return tradeRepository.findForUpdate(classroomId, tradeId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The trade could not be found."
            ));
    }

    private static void validateCreate(CreateTradeCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.counterpartStudentId() == null
                || command.offeredAmount() < 0
                || command.requestedAmount() < 0
                || command.offeredItemIds() == null
                || command.requestedItemIds() == null
                || command.offeredItemIds().stream().anyMatch(java.util.Objects::isNull)
                || command.requestedItemIds().stream().anyMatch(java.util.Objects::isNull)
        ) {
            throw validation("Check the personal trade request values.");
        }
        List<UUID> allItems = new ArrayList<>(command.offeredItemIds());
        allItems.addAll(command.requestedItemIds());
        Set<UUID> uniqueItems = new HashSet<>(allItems);
        if (uniqueItems.size() != allItems.size()) {
            throw validation("An item can appear only once in a trade.");
        }
        if (
            command.offeredAmount() == 0
                && command.requestedAmount() == 0
                && uniqueItems.isEmpty()
        ) {
            throw validation("A trade must include money or at least one item.");
        }
    }

    private static void validateProposerCommand(ProposerTradeCommandRequest command) {
        if (
            command == null
                || command.classroomId() == null
                || command.tradeId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.command() != ProposerTradeCommand.CANCEL
        ) {
            throw validation("Check the proposer command values.");
        }
    }

    private static void validateCounterpartDecision(
        CounterpartTradeDecisionRequest command
    ) {
        if (
            command == null
                || command.classroomId() == null
                || command.tradeId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.decision() == null
        ) {
            throw validation("Check the counterpart decision values.");
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

    private static UUID parseStudentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid student session is required."
            );
        }
    }

    private static String hashCreate(CreateTradeCommand command) {
        return sha256(
            command.classroomId() + "|" + command.counterpartStudentId() + "|"
                + command.offeredAmount() + "|" + command.requestedAmount() + "|"
                + canonicalIds(command.offeredItemIds()) + "|"
                + canonicalIds(command.requestedItemIds()) + "|"
                + normalized(command.description()) + "|" + command.expectedStateVersion()
        );
    }

    private static String canonicalIds(List<UUID> ids) {
        return ids.stream()
            .sorted(Comparator.naturalOrder())
            .map(UUID::toString)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the trade request.", exception);
        }
    }

    private static UUID responseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous trade result is invalid."
            );
        }
    }

    private static TradeCommandResult result(Trade trade) {
        return new TradeCommandResult(
            trade.getId(),
            trade.getStatus().name(),
            trade.getStateVersion()
        );
    }

    private static ClassroomOperationException validation(String message) {
        return operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
