package com.b201.ecovillage.ai.application;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.domain.AiDecision;
import com.b201.ecovillage.ai.domain.AiProposal;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.repository.AiDecisionRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.application.ModuleActivationCommand;
import com.b201.ecovillage.classroom.application.ModuleActivationResult;
import com.b201.ecovillage.classroom.application.ModuleActivationService;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class AiProposalCommandService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/ai/balancing-proposals/{proposal_id}/commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final AiProposalRepository proposalRepository;
    private final AiRunRepository runRepository;
    private final AiDecisionRepository decisionRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final ModuleActivationService moduleActivationService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public AiProposalCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        AiProposalRepository proposalRepository,
        AiRunRepository runRepository,
        AiDecisionRepository decisionRepository,
        ClassroomPolicyVersionRepository policyRepository,
        ModuleActivationService moduleActivationService,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        EntityManager entityManager,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.proposalRepository = proposalRepository;
        this.runRepository = runRepository;
        this.decisionRepository = decisionRepository;
        this.policyRepository = policyRepository;
        this.moduleActivationService = moduleActivationService;
        this.idempotencyRepository = idempotencyRepository;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public AiProposalCommandResult execute(AiProposalCommand command) {
        validate(command);
        ClassroomPermission permission = command.command() == AiProposalCommandType.ACKNOWLEDGE
            ? ClassroomPermission.ACKNOWLEDGE_AI_PROPOSAL
            : ClassroomPermission.APPLY_AI_PROPOSAL;
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            permission,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A teacher session is required."));
        String requestHash = hash(command);
        AiProposalCommandResult replay = replay(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }
        AiProposal proposal = proposalRepository
            .findByClassroomIdAndId(command.classroomId(), command.proposalId())
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The AI proposal was not found."));
        AiRun run = runRepository.findByClassroomIdAndId(command.classroomId(), proposal.getAiRunId())
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The AI run was not found."));
        Instant now = clock.instant();
        if (!now.isBefore(proposal.getExpiresAt())) {
            throw operation(HttpStatus.CONFLICT, "PROPOSAL_EXPIRED", "The AI proposal has expired.");
        }
        AiProposalCommandResult result = switch (command.command()) {
            case ACKNOWLEDGE -> acknowledge(command, teacher, proposal, run, now);
            case REJECT -> reject(command, teacher, proposal, run, now);
            case APPLY -> apply(command, teacher, proposal, run, now);
        };
        remember(command, teacher.getId(), requestHash, result, clock.instant());
        return result;
    }

    private AiProposalCommandResult replay(
        AiProposalCommand command,
        UUID actorId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository.findForReplay(
            command.classroomId(),
            actorId,
            ROUTE,
            command.requestId().toString()
        ).orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        return objectMapper.convertValue(record.getResponseBody(), AiProposalCommandResult.class);
    }

    private void remember(
        AiProposalCommand command,
        UUID actorId,
        String requestHash,
        AiProposalCommandResult result,
        Instant now
    ) {
        idempotencyRepository.save(IdempotencyRecord.completed(
            uuidGenerator.generate(),
            command.classroomId(),
            actorId,
            ROUTE,
            command.requestId().toString(),
            requestHash,
            HttpStatus.OK.value(),
            toMap(result),
            null,
            now.plus(IDEMPOTENCY_TTL),
            now
        ));
    }

    private String hash(AiProposalCommand command) {
        try {
            Map<String, Object> value = new java.util.TreeMap<>();
            value.put("classroom_id", command.classroomId().toString());
            value.put("proposal_id", command.proposalId().toString());
            value.put("command", command.command().name());
            value.put("edited_values", new java.util.TreeMap<>(command.editedValues()));
            value.put("reason", command.reason());
            value.put("expected_policy_version", command.expectedPolicyVersion());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                objectMapper.writeValueAsBytes(value)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the AI proposal command.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        Map<String, Object> result = objectMapper.convertValue(value, Map.class);
        result.values().removeIf(java.util.Objects::isNull);
        return result;
    }

    private AiProposalCommandResult acknowledge(
        AiProposalCommand command,
        UserAccount teacher,
        AiProposal proposal,
        AiRun run,
        Instant now
    ) {
        if ("ACKNOWLEDGED".equals(proposal.getStatus())) {
            throw operation(HttpStatus.CONFLICT, "ALREADY_ACKNOWLEDGED", "The proposal was already acknowledged.");
        }
        requireState(proposal, Set.of("AWAITING_TEACHER"));
        proposal.acknowledge(now);
        run.acknowledge(now);
        decisionRepository.save(decision(command, teacher, "ACKNOWLEDGE", now));
        return result(proposal, run, "ACKNOWLEDGED", null, List.of());
    }

    private AiProposalCommandResult reject(
        AiProposalCommand command,
        UserAccount teacher,
        AiProposal proposal,
        AiRun run,
        Instant now
    ) {
        requireState(proposal, Set.of("AWAITING_TEACHER", "ACKNOWLEDGED"));
        if (decisionRepository.existsByAiProposalIdAndDecision(proposal.getId(), "REJECT")) {
            throw operation(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "The proposal was already rejected.");
        }
        proposal.reject(now);
        run.reject(now);
        decisionRepository.save(decision(command, teacher, "REJECT", now));
        return result(proposal, run, "REJECTED", null, List.of());
    }

    private AiProposalCommandResult apply(
        AiProposalCommand command,
        UserAccount teacher,
        AiProposal proposal,
        AiRun run,
        Instant now
    ) {
        requireState(proposal, Set.of("AWAITING_TEACHER", "ACKNOWLEDGED"));
        if ("BLOCK".equals(proposal.getSeverity())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "A blocked setup proposal cannot be applied."
            );
        }
        Map<String, Object> proposed = mutableMap(proposal.getProposedValues());
        String moduleCode = text(proposed.get("module_code"));
        if (moduleCode == null) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_RECOMMENDATION_CONTEXT",
                "This setup proposal is not a module activation proposal."
            );
        }
        String policyArea = policyArea(moduleCode);
        ClassroomPolicyVersion current = policyArea == null
            ? null
            : policyRepository.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                policyArea
            ).orElse(null);
        validatePolicyVersion(command.expectedPolicyVersion(), current);
        List<AiProposalCommandResult.AppliedValue> appliedValues = applyEdits(
            proposed,
            proposal.getAllowedRanges(),
            command.editedValues()
        );
        proposal.replaceProposedValues(proposed, now);
        proposal.approve(now);
        run.approve(now);
        proposalRepository.flush();
        runRepository.flush();
        ModuleActivationResult activation = moduleActivationService.activate(
            new ModuleActivationCommand(
                command.teacherSubject(),
                command.classroomId(),
                moduleCode,
                command.requestId(),
                command.correlationId(),
                number(proposed.get("module_state_version")),
                true,
                map(proposed.get("initial_policy_values")),
                stringList(proposed.get("recommendation_action_ids"))
            )
        );
        entityManager.refresh(run);
        Instant completedAt = clock.instant();
        proposal.applied(activation.policyVersionId(), completedAt);
        run.complete(completedAt);
        decisionRepository.save(decision(command, teacher, "APPROVE", completedAt));
        return result(proposal, run, "APPLIED", activation, appliedValues);
    }

    private List<AiProposalCommandResult.AppliedValue> applyEdits(
        Map<String, Object> proposed,
        Map<String, Object> allowed,
        Map<String, Long> edits
    ) {
        List<Map<String, Object>> items = mapList(proposed.get("items"));
        Map<String, Map<String, Object>> ranges = new LinkedHashMap<>();
        mapList(allowed.get("items")).forEach(value -> ranges.put(text(value.get("action_id")), value));
        if (!ranges.keySet().containsAll(edits.keySet())) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "An edited action is not in the proposal.");
        }
        List<AiProposalCommandResult.AppliedValue> result = new ArrayList<>();
        Map<String, Long> finalValues = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String actionId = text(item.get("action_id"));
            long value = edits.getOrDefault(actionId, number(item.get("proposed_value")));
            Map<String, Object> range = ranges.get(actionId);
            long min = number(range.get("allowed_min_value"));
            long max = number(range.get("allowed_max_value"));
            if (value < min || value > max) {
                throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_VIOLATION", "An edited value is outside the allowed range.");
            }
            item.put("proposed_value", value);
            finalValues.put(actionId, value);
            result.add(new AiProposalCommandResult.AppliedValue(actionId, value, text(item.get("unit"))));
        }
        Map<String, Object> initialPolicy = map(proposed.get("initial_policy_values"));
        if (initialPolicy.containsKey("rate_bps")) {
            initialPolicy.put("rate_bps", result.getFirst().proposedValue());
        }
        if (initialPolicy.get("roles") instanceof List<?>) {
            List<Map<String, Object>> roles = mapList(initialPolicy.get("roles"));
            for (int index = 0; index < Math.min(roles.size(), result.size()); index++) {
                roles.get(index).put("gross_amount", result.get(index).proposedValue());
            }
            initialPolicy.put("roles", roles);
        }
        proposed.put("items", items);
        proposed.put("initial_policy_values", initialPolicy);
        return result;
    }

    private AiDecision decision(
        AiProposalCommand command,
        UserAccount teacher,
        String decision,
        Instant now
    ) {
        return AiDecision.create(
            uuidGenerator.generate(),
            command.classroomId(),
            command.proposalId(),
            teacher.getId(),
            decision,
            command.reason(),
            now
        );
    }

    private static AiProposalCommandResult result(
        AiProposal proposal,
        AiRun run,
        String status,
        ModuleActivationResult activation,
        List<AiProposalCommandResult.AppliedValue> values
    ) {
        return new AiProposalCommandResult(
            proposal.getId(),
            run.getId(),
            status,
            activation == null ? null : activation.policyVersion(),
            activation == null ? null : activation.policyVersionId(),
            values,
            proposal.getStateVersion()
        );
    }

    private static void validatePolicyVersion(
        Integer expectedVersion,
        ClassroomPolicyVersion current
    ) {
        if (current == null && expectedVersion != null && expectedVersion != 0) {
            throw operation(HttpStatus.CONFLICT, "POLICY_VERSION_CONFLICT", "No current policy version exists.");
        }
        if (current != null && !Integer.valueOf(current.getVersion()).equals(expectedVersion)) {
            throw operation(HttpStatus.CONFLICT, "POLICY_VERSION_CONFLICT", "The current policy version has changed.");
        }
    }

    private static void requireState(AiProposal proposal, Set<String> allowed) {
        if (!allowed.contains(proposal.getStatus())) {
            throw operation(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "The proposal is not awaiting a teacher decision.");
        }
    }

    private static void validate(AiProposalCommand command) {
        if (
            command == null || command.classroomId() == null || command.proposalId() == null
                || command.requestId() == null || command.correlationId() == null
                || command.command() == null || command.reason() == null
                || command.reason().isBlank() || command.reason().length() > 1000
        ) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the AI proposal command.");
        }
    }

    private static String policyArea(String moduleCode) {
        return switch (moduleCode.toUpperCase(Locale.ROOT)) {
            case "ROLE_SALARY", "SALARY" -> "SALARY";
            case "INCOME_TAX" -> "INCOME_TAX_RATE";
            case "DEPOSIT" -> "DEPOSIT_RATE";
            case "LOAN" -> "LOAN_RATE";
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Map<String, Object> value) {
        return new LinkedHashMap<>(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream().map(AiProposalCommandService::map).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
