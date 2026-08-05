package com.b201.ecovillage.ai.application;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.domain.QuizSessionStatus;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.ruleengine.domain.RuleCandidate;
import com.b201.ecovillage.ruleengine.domain.RuleEngineResult;
import com.b201.ecovillage.ruleengine.domain.RuleTargetType;
import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.domain.StoreStock;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;

import tools.jackson.databind.ObjectMapper;

@Service
class SetupProposalTargetSnapshotService {

    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final StoreProductRepository productRepository;
    private final StoreProductVersionRepository productVersionRepository;
    private final StoreStockRepository stockRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final ObjectMapper objectMapper;

    SetupProposalTargetSnapshotService(
        ClassroomModuleRepository moduleRepository,
        ClassroomPolicyVersionRepository policyRepository,
        StoreProductRepository productRepository,
        StoreProductVersionRepository productVersionRepository,
        StoreStockRepository stockRepository,
        QuizSessionRepository quizSessionRepository,
        ObjectMapper objectMapper
    ) {
        this.moduleRepository = moduleRepository;
        this.policyRepository = policyRepository;
        this.productRepository = productRepository;
        this.productVersionRepository = productVersionRepository;
        this.stockRepository = stockRepository;
        this.quizSessionRepository = quizSessionRepository;
        this.objectMapper = objectMapper;
    }

    SetupProposalTargetSnapshot capture(
        Classroom classroom,
        SetupPrecheckInput input,
        RuleEngineResult rules
    ) {
        RuleCandidate candidate = requireSingleTarget(rules);
        Map<String, Integer> policies = policyVersions(classroom.getId());
        Map<String, Object> snapshot = baseSnapshot(classroom, input, policies);
        Target target = switch (input.contextType()) {
            case MODULE_ACTIVATION -> moduleTarget(classroom.getId(), input, candidate);
            case PRODUCT_DRAFT -> productTarget(classroom.getId(), input, candidate);
            case QUIZ_SESSION_DRAFT -> quizTarget(classroom.getId(), input, candidate);
            case CLASSROOM_SETUP -> throw invalidContext(
                "A classroom setup check does not have an applicable target."
            );
        };
        snapshot.put("target", target.snapshot());
        String policyArea = switch (input.contextType()) {
            case MODULE_ACTIVATION -> modulePolicyArea(input.moduleActivation().moduleCode());
            case PRODUCT_DRAFT -> "STORE";
            case QUIZ_SESSION_DRAFT -> "QUIZ_REWARD";
            case CLASSROOM_SETUP -> null;
        };
        int primaryPolicyVersion = policyArea == null
            ? 0
            : policies.getOrDefault(policyArea, 0);
        return new SetupProposalTargetSnapshot(
            input.contextType().name(),
            candidate.targetType().name(),
            target.id(),
            target.version(),
            primaryPolicyVersion,
            snapshot,
            fingerprint(snapshot)
        );
    }

    private Map<String, Object> baseSnapshot(
        Classroom classroom,
        SetupPrecheckInput input,
        Map<String, Integer> policies
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("classroom_state_version", classroom.getStateVersion());
        snapshot.put("policy_versions", policies);
        snapshot.put(
            "active_modules",
            moduleRepository
                .findAllByClassroomIdAndStatusOrderByModuleCode(classroom.getId(), "ACTIVE")
                .stream()
                .map(module -> Map.<String, Object>of(
                    "module_code", module.getModuleCode(),
                    "state_version", module.getStateVersion()
                ))
                .toList()
        );
        snapshot.put("draft_input", inputSnapshot(input));
        return snapshot;
    }

    private Map<String, Object> inputSnapshot(SetupPrecheckInput input) {
        Object value = switch (input.contextType()) {
            case MODULE_ACTIVATION -> input.moduleActivation();
            case PRODUCT_DRAFT -> input.productDraft();
            case QUIZ_SESSION_DRAFT -> input.quizDraft();
            case CLASSROOM_SETUP -> Map.of();
        };
        return toMap(value);
    }

    private Target moduleTarget(
        UUID classroomId,
        SetupPrecheckInput input,
        RuleCandidate candidate
    ) {
        SetupPrecheckInput.ModuleActivation draft = input.moduleActivation();
        if (draft == null || draft.targetId() == null) {
            throw invalidContext("The module activation target is missing.");
        }
        ClassroomModule module = moduleRepository
            .findByClassroomIdAndModuleCode(classroomId, draft.moduleCode().trim().toUpperCase())
            .orElseThrow(() -> notFound("The module target was not found."));
        requireTarget(candidate, module.getId());
        RuleTargetType expectedTargetType = moduleTargetType(module.getModuleCode());
        if (expectedTargetType != null && candidate.targetType() != expectedTargetType) {
            throw invalidContext("The module candidate type does not match the target module.");
        }
        if (!draft.targetId().equals(module.getId())) {
            throw invalidContext("The module target does not match the proposal.");
        }
        return new Target(module.getId(), module.getStateVersion(), Map.of(
            "module_id", module.getId().toString(),
            "module_code", module.getModuleCode(),
            "status", module.getStatus(),
            "state_version", module.getStateVersion()
        ));
    }

    private Target productTarget(
        UUID classroomId,
        SetupPrecheckInput input,
        RuleCandidate candidate
    ) {
        SetupPrecheckInput.ProductDraft draft = input.productDraft();
        if (draft == null || draft.targetId() == null) {
            throw invalidContext("The product draft target is missing.");
        }
        StoreProduct product = productRepository
            .findByClassroomIdAndId(classroomId, draft.targetId())
            .orElseThrow(() -> notFound("The product target was not found."));
        requireTarget(candidate, product.getId());
        if (candidate.targetType() != RuleTargetType.PRODUCT_PRICE) {
            throw invalidContext("The product candidate type must be PRODUCT_PRICE.");
        }
        if (!product.canReviseDraft()) {
            throw invalidContext("A published product cannot be used as a product draft target.");
        }
        StoreProductVersion version = productVersionRepository
            .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
                classroomId,
                product.getId()
            )
            .orElseThrow(() -> stale("The product version is missing."));
        StoreStock stock = stockRepository
            .findByClassroomIdAndStoreProductId(classroomId, product.getId())
            .orElseThrow(() -> stale("The product stock is missing."));
        if (
            (draft.stock() != null && draft.stock() != stock.getAvailableQuantity())
                || (draft.vatBps() != null && draft.vatBps() != version.getVatRateBps())
                || (
                    draft.confirmedPrice() != null
                        && draft.confirmedPrice() != version.getUnitPrice()
                )
        ) {
            throw invalidContext(
                "The product draft values do not match the persisted product target."
            );
        }
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("product_id", product.getId().toString());
        target.put("name", product.getName());
        target.put("description", product.getDescription());
        target.put("product_kind", product.getProductKind().name());
        target.put("status", product.getStatus().name());
        target.put("state_version", product.getStateVersion());
        target.put("product_version", version.getVersion());
        target.put("unit_price", version.getUnitPrice());
        target.put("vat_rate_bps", version.getVatRateBps());
        target.put("stock_quantity", stock.getAvailableQuantity());
        target.put("stock_state_version", stock.getStateVersion());
        target.put("metadata", version.getMetadata());
        return new Target(product.getId(), version.getVersion(), target);
    }

    private Target quizTarget(
        UUID classroomId,
        SetupPrecheckInput input,
        RuleCandidate candidate
    ) {
        SetupPrecheckInput.QuizDraft draft = input.quizDraft();
        if (draft == null || draft.targetId() == null) {
            throw invalidContext("The quiz session draft target is missing.");
        }
        QuizSession session = quizSessionRepository
            .findByClassroomIdAndId(classroomId, draft.targetId())
            .orElseThrow(() -> notFound("The quiz session target was not found."));
        requireTarget(candidate, session.getId());
        if (candidate.targetType() != RuleTargetType.GAME_REWARD) {
            throw invalidContext("The quiz candidate type must be GAME_REWARD.");
        }
        if (draft.mode() == null || !session.getMode().name().equals(draft.mode().name())) {
            throw invalidContext("The quiz mode does not match the target session.");
        }
        if (
            draft.category() == null
                || !session.getContentType().name().equals(draft.category().name())
        ) {
            throw invalidContext("The quiz category does not match the target session.");
        }
        if (session.getMode() == QuizMode.TEST) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FORBIDDEN_REWARD_CONTEXT",
                "A TEST quiz cannot receive a monetary reward."
            );
        }
        if (session.getStatus() != QuizSessionStatus.OPEN) {
            throw stale("Only an open GAME session can receive a reward proposal.");
        }
        long currentReward = value(session.getRewardPolicyPublic().get("reward_per_person"), 0L);
        if (
            draft.confirmedRewardPerPerson() != null
                && draft.confirmedRewardPerPerson() != currentReward
        ) {
            throw invalidContext(
                "The quiz reward draft does not match the persisted session target."
            );
        }
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("quiz_session_id", session.getId().toString());
        target.put("mode", session.getMode().name());
        target.put("content_type", session.getContentType().name());
        target.put("status", session.getStatus().name());
        target.put("state_version", session.getStateVersion());
        target.put("reward_policy_public", session.getRewardPolicyPublic());
        target.put("leaderboard_public", session.isLeaderboardPublic());
        return new Target(session.getId(), session.getStateVersion(), target);
    }

    private RuleCandidate requireSingleTarget(RuleEngineResult rules) {
        if (rules.candidates().isEmpty()) {
            throw invalidContext("The setup proposal does not contain an applicable candidate.");
        }
        RuleCandidate first = rules.candidates().getFirst();
        boolean sameTarget = rules.candidates().stream().allMatch(candidate ->
            candidate.targetType() == first.targetType()
                && candidate.targetId().equals(first.targetId())
        );
        if (!sameTarget) {
            throw invalidContext("The setup proposal contains multiple domain targets.");
        }
        return first;
    }

    private static void requireTarget(RuleCandidate candidate, UUID targetId) {
        if (!targetId.equals(candidate.targetId())) {
            throw invalidContext("The rule candidate does not match the domain target.");
        }
    }

    private Map<String, Integer> policyVersions(UUID classroomId) {
        Map<String, Integer> versions = new TreeMap<>();
        for (ClassroomPolicyVersion policy : policyRepository
            .findAllByClassroomIdAndSupersededAtIsNullOrderByPolicyArea(classroomId)) {
            versions.put(policy.getPolicyArea(), policy.getVersion());
        }
        return versions;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private String fingerprint(Map<String, Object> value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                objectMapper.writeValueAsBytes(canonical(value))
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not fingerprint the setup proposal target.", exception);
        }
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sorted = new TreeMap<>();
            source.forEach((key, nested) -> sorted.put(String.valueOf(key), canonical(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(canonical(item)));
            return result;
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(String::valueOf).sorted().toList();
        }
        return value;
    }

    private static String modulePolicyArea(String moduleCode) {
        if (moduleCode == null) {
            return null;
        }
        return switch (moduleCode.trim().toUpperCase()) {
            case "ROLE_SALARY", "SALARY" -> "SALARY";
            case "INCOME_TAX" -> "INCOME_TAX_RATE";
            case "DEPOSIT" -> "DEPOSIT_RATE";
            case "SAVINGS" -> "SAVINGS_RATE";
            case "LOAN" -> "LOAN_RATE";
            default -> null;
        };
    }

    private static RuleTargetType moduleTargetType(String moduleCode) {
        if (moduleCode == null) {
            return null;
        }
        return switch (moduleCode.trim().toUpperCase()) {
            case "ROLE_SALARY", "SALARY" -> RuleTargetType.ROLE_SALARY;
            case "INCOME_TAX" -> RuleTargetType.INCOME_TAX_RATE;
            case "DEPOSIT" -> RuleTargetType.DEPOSIT_RATE;
            case "SAVINGS" -> RuleTargetType.SAVINGS_RATE;
            case "LOAN" -> RuleTargetType.LOAN_RATE;
            default -> null;
        };
    }

    private static long value(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static ClassroomOperationException invalidContext(String message) {
        return operation(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RECOMMENDATION_CONTEXT", message);
    }

    private static ClassroomOperationException notFound(String message) {
        return operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    private static ClassroomOperationException stale(String message) {
        return operation(HttpStatus.CONFLICT, "STALE_PROPOSAL", message);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private record Target(UUID id, long version, Map<String, Object> snapshot) {
    }
}
