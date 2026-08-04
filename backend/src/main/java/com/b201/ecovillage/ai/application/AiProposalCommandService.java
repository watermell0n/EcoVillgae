package com.b201.ecovillage.ai.application;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.application.ModuleActivationCommand;
import com.b201.ecovillage.classroom.application.ModuleActivationResult;
import com.b201.ecovillage.classroom.application.ModuleActivationService;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.application.QuizSessionCommand;
import com.b201.ecovillage.quiz.application.QuizSessionCommandService;
import com.b201.ecovillage.quiz.application.QuizSessionCommandType;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.ruleengine.domain.RuleEngineResult;
import com.b201.ecovillage.ruleengine.domain.RuleSeverity;
import com.b201.ecovillage.store.application.StoreProductCommand;
import com.b201.ecovillage.store.application.StoreProductCommandService;
import com.b201.ecovillage.store.application.StoreProductCommandType;
import com.b201.ecovillage.store.domain.StoreProductKind;

import tools.jackson.databind.ObjectMapper;

@Service
public class AiProposalCommandService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/ai/balancing-proposals/{proposal_id}/commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final AiProposalRepository proposalRepository;
    private final AiRunRepository runRepository;
    private final AiDecisionRepository decisionRepository;
    private final ModuleActivationService moduleActivationService;
    private final StoreProductCommandService storeProductCommandService;
    private final QuizSessionCommandService quizSessionCommandService;
    private final SetupPrecheckOperationService setupPrecheckService;
    private final SetupProposalTargetSnapshotService targetSnapshotService;
    private final OperationalBalancingApplyService operationalApplyService;
    private final AuditTrailService auditTrailService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public AiProposalCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        AiProposalRepository proposalRepository,
        AiRunRepository runRepository,
        AiDecisionRepository decisionRepository,
        ModuleActivationService moduleActivationService,
        StoreProductCommandService storeProductCommandService,
        QuizSessionCommandService quizSessionCommandService,
        SetupPrecheckOperationService setupPrecheckService,
        SetupProposalTargetSnapshotService targetSnapshotService,
        OperationalBalancingApplyService operationalApplyService,
        AuditTrailService auditTrailService,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        EntityManager entityManager,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.proposalRepository = proposalRepository;
        this.runRepository = runRepository;
        this.decisionRepository = decisionRepository;
        this.moduleActivationService = moduleActivationService;
        this.storeProductCommandService = storeProductCommandService;
        this.quizSessionCommandService = quizSessionCommandService;
        this.setupPrecheckService = setupPrecheckService;
        this.targetSnapshotService = targetSnapshotService;
        this.operationalApplyService = operationalApplyService;
        this.auditTrailService = auditTrailService;
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
        String previousStatus = proposal.getStatus();
        proposal.acknowledge(now);
        run.acknowledge(now);
        decisionRepository.save(decision(command, teacher, "ACKNOWLEDGE", now));
        recordDecisionAudit(
            command,
            teacher,
            proposal,
            previousStatus,
            "ACKNOWLEDGED",
            Map.of()
        );
        return result(proposal, run, "ACKNOWLEDGED", null, null, List.of());
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
        String previousStatus = proposal.getStatus();
        proposal.reject(now);
        run.reject(now);
        decisionRepository.save(decision(command, teacher, "REJECT", now));
        recordDecisionAudit(
            command,
            teacher,
            proposal,
            previousStatus,
            "REJECTED",
            Map.of()
        );
        return result(proposal, run, "REJECTED", null, null, List.of());
    }

    private AiProposalCommandResult apply(
        AiProposalCommand command,
        UserAccount teacher,
        AiProposal proposal,
        AiRun run,
        Instant now
    ) {
        requireState(proposal, Set.of("AWAITING_TEACHER", "ACKNOWLEDGED"));
        if ("BALANCING_RECOMMENDATION".equals(proposal.getProposalType())) {
            return applyOperational(command, teacher, proposal, run, now);
        }
        if ("BLOCK".equals(proposal.getSeverity())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "A blocked setup proposal cannot be applied."
            );
        }
        Map<String, Object> proposed = mutableMap(proposal.getProposedValues());
        SetupPrecheckInput originalInput = proposalInput(proposed);
        RuleEngineResult currentRules = setupPrecheckService.revalidate(
            command.classroomId(),
            originalInput
        );
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom was not found."
            ));
        SetupProposalTargetSnapshot currentTarget = targetSnapshotService.capture(
            classroom,
            originalInput,
            currentRules
        );
        validateTargetSnapshot(proposed, currentTarget);
        validateExpectedPolicyVersion(
            command.expectedPolicyVersion(),
            intNumber(proposed.get("policy_version"))
        );
        List<Map<String, Object>> originalItems = mapList(proposed.get("items"));
        List<AiProposalCommandResult.AppliedValue> appliedValues = applyEdits(
            proposed,
            proposal.getAllowedRanges(),
            command.editedValues()
        );
        SetupPrecheckInput editedInput = withAppliedValues(originalInput, appliedValues);
        RuleEngineResult editedRules = setupPrecheckService.revalidate(
            command.classroomId(),
            editedInput
        );
        validateSpringVerdict(proposed, editedRules);
        proposal.replaceProposedValues(proposed, now);
        proposal.approve(now);
        run.approve(now);
        proposalRepository.flush();
        runRepository.flush();
        DomainApplication application = applyToDomain(
            command,
            proposed,
            currentTarget,
            appliedValues
        );
        entityManager.refresh(run);
        Instant completedAt = clock.instant();
        proposal.applied(application.policyVersionId(), completedAt);
        run.complete(completedAt);
        decisionRepository.save(decision(command, teacher, "APPROVE", completedAt));
        recordApplyAudit(
            command,
            teacher,
            proposal,
            currentTarget,
            originalItems,
            appliedValues
        );
        return result(
            proposal,
            run,
            "APPLIED",
            application.policyVersion(),
            application.policyVersionId(),
            appliedValues
        );
    }

    private AiProposalCommandResult applyOperational(
        AiProposalCommand command,
        UserAccount teacher,
        AiProposal proposal,
        AiRun run,
        Instant now
    ) {
        String previousStatus = proposal.getStatus();
        Map<String, Object> proposed = mutableMap(proposal.getProposedValues());
        List<Map<String, Object>> originalItems = mapList(proposed.get("items"));
        List<AiProposalCommandResult.AppliedValue> appliedValues = applyEdits(
            proposed,
            proposal.getAllowedRanges(),
            command.editedValues()
        );
        operationalApplyService.apply(
            command.classroomId(),
            proposed,
            proposal.getAllowedRanges(),
            appliedValues
        );
        proposal.replaceProposedValues(proposed, now);
        proposal.approve(now);
        run.approve(now);
        proposalRepository.flush();
        runRepository.flush();
        Instant completedAt = clock.instant();
        proposal.applied(null, completedAt);
        run.complete(completedAt);
        decisionRepository.save(decision(command, teacher, "APPROVE", completedAt));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("original_values", originalItems);
        details.put("edited_values", command.editedValues());
        details.put("applied_values", appliedValues);
        recordDecisionAudit(
            command,
            teacher,
            proposal,
            previousStatus,
            "APPLIED",
            details
        );
        return result(proposal, run, "APPLIED", null, null, appliedValues);
    }

    private DomainApplication applyToDomain(
        AiProposalCommand command,
        Map<String, Object> proposed,
        SetupProposalTargetSnapshot target,
        List<AiProposalCommandResult.AppliedValue> appliedValues
    ) {
        String contextType = text(proposed.get("context_type"));
        if (contextType == null) {
            throw invalidContext("The proposal context type is missing.");
        }
        return switch (contextType) {
            case "MODULE_ACTIVATION" -> applyModule(command, proposed);
            case "PRODUCT_DRAFT" -> applyProduct(command, proposed, target, appliedValues);
            case "QUIZ_SESSION_DRAFT" -> applyQuizReward(
                command,
                proposed,
                target,
                appliedValues
            );
            default -> throw invalidContext("The proposal context cannot be applied.");
        };
    }

    private DomainApplication applyModule(
        AiProposalCommand command,
        Map<String, Object> proposed
    ) {
        String moduleCode = text(proposed.get("module_code"));
        if (moduleCode == null) {
            throw invalidContext("The module activation proposal has no module code.");
        }
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
        return new DomainApplication(
            activation.policyVersion(),
            activation.policyVersionId()
        );
    }

    private DomainApplication applyProduct(
        AiProposalCommand command,
        Map<String, Object> proposed,
        SetupProposalTargetSnapshot target,
        List<AiProposalCommandResult.AppliedValue> appliedValues
    ) {
        if (appliedValues.size() != 1 || !"PRODUCT_PRICE".equals(target.targetType())) {
            throw invalidContext("The product proposal must contain one price candidate.");
        }
        Map<String, Object> snapshot = map(proposed.get("draft_snapshot"));
        Map<String, Object> product = map(snapshot.get("target"));
        Map<String, Object> metadata = map(product.get("metadata"));
        storeProductCommandService.execute(new StoreProductCommand(
            command.teacherSubject(),
            command.classroomId(),
            command.requestId(),
            command.correlationId(),
            number(snapshot.get("classroom_state_version")),
            intNumber(proposed.get("policy_version")),
            StoreProductCommandType.UPDATE,
            target.targetId(),
            text(product.get("name")),
            text(product.get("description")),
            StoreProductKind.valueOf(text(product.get("product_kind"))),
            appliedValues.getFirst().proposedValue(),
            intNumber(product.get("vat_rate_bps")),
            intNumber(product.get("stock_quantity")),
            uuidOrNull(metadata.get("character_source_id")),
            Math.toIntExact(target.targetVersion())
        ));
        return DomainApplication.none();
    }

    private DomainApplication applyQuizReward(
        AiProposalCommand command,
        Map<String, Object> proposed,
        SetupProposalTargetSnapshot target,
        List<AiProposalCommandResult.AppliedValue> appliedValues
    ) {
        if (appliedValues.size() != 1 || !"GAME_REWARD".equals(target.targetType())) {
            throw invalidContext("The quiz proposal must contain one GAME reward candidate.");
        }
        Map<String, Object> snapshot = map(proposed.get("draft_snapshot"));
        Map<String, Object> session = map(snapshot.get("target"));
        if (!"GAME".equals(text(session.get("mode")))) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FORBIDDEN_REWARD_CONTEXT",
                "A TEST quiz cannot receive a monetary reward."
            );
        }
        Map<String, Object> rewardPolicy = map(session.get("reward_policy_public"));
        rewardPolicy.put("reward_per_person", appliedValues.getFirst().proposedValue());
        quizSessionCommandService.execute(new QuizSessionCommand(
            command.teacherSubject(),
            command.classroomId(),
            command.requestId(),
            command.correlationId(),
            target.targetVersion(),
            target.targetId(),
            QuizSessionCommandType.UPDATE_REWARD,
            List.of(),
            List.of(),
            QuizMode.GAME,
            null,
            rewardPolicy,
            booleanValue(session.get("leaderboard_public"))
        ));
        return DomainApplication.none();
    }

    private SetupPrecheckInput proposalInput(Map<String, Object> proposed) {
        Object value = proposed.get("rule_input");
        if (!(value instanceof Map<?, ?>)) {
            throw invalidContext("The proposal rule input snapshot is missing.");
        }
        try {
            return objectMapper.convertValue(value, SetupPrecheckInput.class);
        } catch (RuntimeException exception) {
            throw invalidContext("The proposal rule input snapshot is invalid.");
        }
    }

    private SetupPrecheckInput withAppliedValues(
        SetupPrecheckInput input,
        List<AiProposalCommandResult.AppliedValue> appliedValues
    ) {
        if (appliedValues.isEmpty()) {
            return input;
        }
        SetupPrecheckInput.ModuleActivation module = input.moduleActivation();
        SetupPrecheckInput.SalaryPolicy salary = input.salaryPolicy();
        SetupPrecheckInput.ProductDraft product = input.productDraft();
        SetupPrecheckInput.QuizDraft quiz = input.quizDraft();
        if (input.contextType().name().equals("MODULE_ACTIVATION") && module != null) {
            module = new SetupPrecheckInput.ModuleActivation(
                module.moduleCode(),
                module.targetId(),
                appliedValues.getFirst().proposedValue(),
                module.currentDepositRateBps()
            );
            if (salary != null && !salary.roles().isEmpty()) {
                List<SetupPrecheckInput.SalaryRole> roles = new ArrayList<>();
                for (int index = 0; index < salary.roles().size(); index++) {
                    SetupPrecheckInput.SalaryRole role = salary.roles().get(index);
                    long selected = appliedValues.get(
                        Math.min(index, appliedValues.size() - 1)
                    ).proposedValue();
                    roles.add(new SetupPrecheckInput.SalaryRole(
                        role.roleCode(),
                        role.name(),
                        role.weight(),
                        selected,
                        role.assignedStudentCount(),
                        role.active()
                    ));
                }
                salary = new SetupPrecheckInput.SalaryPolicy(
                    roles,
                    salary.payIntervalDays(),
                    salary.firstPayOn(),
                    salary.defaultRoleSalary()
                );
            }
        }
        if (input.contextType().name().equals("PRODUCT_DRAFT") && product != null) {
            product = new SetupPrecheckInput.ProductDraft(
                product.targetId(),
                product.kind(),
                product.stock(),
                product.expectedDemand(),
                product.expectedPurchaseFrequency(),
                product.vatBps(),
                product.free(),
                appliedValues.getFirst().proposedValue()
            );
        }
        if (input.contextType().name().equals("QUIZ_SESSION_DRAFT") && quiz != null) {
            quiz = new SetupPrecheckInput.QuizDraft(
                quiz.targetId(),
                quiz.mode(),
                quiz.category(),
                quiz.questionCount(),
                quiz.expectedParticipants(),
                quiz.expectedRuns(),
                appliedValues.getFirst().proposedValue()
            );
        }
        return new SetupPrecheckInput(
            input.contextType(),
            input.activeStudentCount(),
            input.systemAmountCap(),
            input.nonRewardIssuance(),
            input.existingRewardIssuance(),
            input.expectedRecovery(),
            input.otherPeriodicIssuancePerStudent(),
            input.effectiveTaxBps(),
            module,
            salary,
            product,
            quiz
        );
    }

    private static void validateTargetSnapshot(
        Map<String, Object> proposed,
        SetupProposalTargetSnapshot current
    ) {
        if (
            !current.contextType().equals(text(proposed.get("context_type")))
                || !current.targetType().equals(text(proposed.get("target_type")))
                || !current.targetId().equals(uuidOrNull(proposed.get("target_id")))
        ) {
            throw invalidContext("The proposal target does not match the current domain target.");
        }
        Map<String, Object> capturedSnapshot = map(proposed.get("draft_snapshot"));
        Map<String, Object> currentSnapshot = current.snapshot();
        if (!map(capturedSnapshot.get("policy_versions")).equals(
            map(currentSnapshot.get("policy_versions")))) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "A policy used by the proposal has changed."
            );
        }
        if (
            number(capturedSnapshot.get("classroom_state_version"))
                != number(currentSnapshot.get("classroom_state_version"))
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state used by the proposal has changed."
            );
        }
        if (
            current.targetVersion() != number(proposed.get("target_version"))
                || !current.fingerprint().equals(text(proposed.get("draft_fingerprint")))
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STALE_PROPOSAL",
                "The proposal target, policy, or classroom state has changed."
            );
        }
    }

    private static void validateExpectedPolicyVersion(Integer expected, int captured) {
        if (expected == null || expected != captured) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "The proposal policy version has changed."
            );
        }
    }

    private static void validateSpringVerdict(
        Map<String, Object> proposed,
        RuleEngineResult rules
    ) {
        if (!text(proposed.get("rule_version")).equals(rules.ruleVersion())) {
            throw operation(
                HttpStatus.CONFLICT,
                "STALE_PROPOSAL",
                "The Spring rule version has changed."
            );
        }
        if (rules.overallSeverity() == RuleSeverity.BLOCK) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "The edited value is blocked by the current Spring rules."
            );
        }
        Set<String> currentActions = rules.candidates().stream()
            .map(candidate -> candidate.actionId())
            .collect(java.util.stream.Collectors.toSet());
        if (!currentActions.containsAll(stringList(proposed.get("recommendation_action_ids")))) {
            throw operation(
                HttpStatus.CONFLICT,
                "STALE_PROPOSAL",
                "The Spring recommendation actions have changed."
            );
        }
    }

    private void recordDecisionAudit(
        AiProposalCommand command,
        UserAccount teacher,
        AiProposal proposal,
        String previousStatus,
        String status,
        Map<String, Object> details
    ) {
        Map<String, Object> after = new LinkedHashMap<>(details);
        after.put("decision_status", status);
        after.put("reason", command.reason());
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "AI_PROPOSAL_" + command.command().name(),
            "AI_PROPOSAL",
            proposal.getId(),
            command.requestId(),
            command.correlationId(),
            Map.of("proposal_status", previousStatus),
            after,
            null,
            null
        );
    }

    private void recordApplyAudit(
        AiProposalCommand command,
        UserAccount teacher,
        AiProposal proposal,
        SetupProposalTargetSnapshot target,
        List<Map<String, Object>> originalItems,
        List<AiProposalCommandResult.AppliedValue> appliedValues
    ) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("proposal_id", proposal.getId().toString());
        before.put("context_type", target.contextType());
        before.put("draft_fingerprint", target.fingerprint());
        before.put("original_values", originalItems);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("decision_status", "APPLIED");
        after.put("edited_values", command.editedValues());
        after.put(
            "applied_values",
            appliedValues.stream().map(value -> Map.<String, Object>of(
                "action_id", value.actionId(),
                "proposed_value", value.proposedValue(),
                "unit", value.unit()
            )).toList()
        );
        after.put(
            "recommendation_action_ids",
            stringList(proposal.getProposedValues().get("recommendation_action_ids"))
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "AI_PROPOSAL_APPLY",
            target.targetType(),
            target.targetId(),
            command.requestId(),
            command.correlationId(),
            before,
            after,
            null,
            null
        );
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
        Integer policyVersion,
        UUID policyVersionId,
        List<AiProposalCommandResult.AppliedValue> values
    ) {
        return new AiProposalCommandResult(
            proposal.getId(),
            run.getId(),
            status,
            policyVersion,
            policyVersionId,
            values,
            proposal.getStateVersion()
        );
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

    private static int intNumber(Object value) {
        return Math.toIntExact(number(value));
    }

    private static UUID uuidOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool
            ? bool
            : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static ClassroomOperationException invalidContext(String message) {
        return operation(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "INVALID_RECOMMENDATION_CONTEXT",
            message
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private record DomainApplication(Integer policyVersion, UUID policyVersionId) {

        private static DomainApplication none() {
            return new DomainApplication(null, null);
        }
    }
}
