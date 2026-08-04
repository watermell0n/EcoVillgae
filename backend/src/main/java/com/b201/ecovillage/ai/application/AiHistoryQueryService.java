package com.b201.ecovillage.ai.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.domain.AiArtifact;
import com.b201.ecovillage.ai.domain.AiDecision;
import com.b201.ecovillage.ai.domain.AiProposal;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiDecisionRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

@Service
public class AiHistoryQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAXIMUM_LIMIT = 100;
    private static final Set<String> EVIDENCE_KEYS = Set.of(
        "evidence_reason_codes",
        "evidence_metric_codes",
        "evidence_fact_ids"
    );

    private final ClassroomAuthorizationService authorizationService;
    private final AiRunRepository runRepository;
    private final AiProposalRepository proposalRepository;
    private final AiArtifactRepository artifactRepository;
    private final AiDecisionRepository decisionRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final Clock clock;

    public AiHistoryQueryService(
        ClassroomAuthorizationService authorizationService,
        AiRunRepository runRepository,
        AiProposalRepository proposalRepository,
        AiArtifactRepository artifactRepository,
        AiDecisionRepository decisionRepository,
        ClassroomPolicyVersionRepository policyRepository,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.runRepository = runRepository;
        this.proposalRepository = proposalRepository;
        this.artifactRepository = artifactRepository;
        this.decisionRepository = decisionRepository;
        this.policyRepository = policyRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiRunHistoryPage findAll(
        String teacherSubject,
        UUID classroomId,
        String cursor,
        Integer requestedLimit
    ) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.READ_AI_HISTORY,
            null
        );
        int limit = limit(requestedLimit);
        Cursor decoded = decode(cursor);
        List<AiRun> queried = runRepository.findHistory(
            classroomId,
            decoded == null ? null : decoded.requestedAt(),
            decoded == null ? null : decoded.runId(),
            PageRequest.of(0, limit + 1)
        );
        boolean hasMore = queried.size() > limit;
        List<AiRun> runs = hasMore ? queried.subList(0, limit) : queried;
        if (runs.isEmpty()) {
            return new AiRunHistoryPage(List.of(), null, false);
        }

        Set<UUID> runIds = runs.stream().map(AiRun::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<AiProposal> proposals = proposalRepository
            .findAllByAiRunIdInOrderByCreatedAtAsc(runIds);
        Map<UUID, List<AiProposal>> proposalsByRun = proposals.stream()
            .collect(Collectors.groupingBy(
                AiProposal::getAiRunId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
        Map<UUID, AiArtifact> artifactByRun = artifactRepository
            .findAllByAiRunIdInOrderByCreatedAtDesc(runIds)
            .stream()
            .collect(Collectors.toMap(
                AiArtifact::getAiRunId,
                Function.identity(),
                (latest, ignored) -> latest,
                LinkedHashMap::new
            ));
        Set<UUID> proposalIds = proposals.stream().map(AiProposal::getId)
            .collect(Collectors.toSet());
        Map<UUID, AiDecision> decisionByProposal = proposalIds.isEmpty()
            ? Map.of()
            : decisionRepository.findAllByAiProposalIdIn(proposalIds).stream()
                .collect(Collectors.toMap(AiDecision::getAiProposalId, Function.identity()));
        Set<UUID> policyIds = proposals.stream()
            .map(AiProposal::getAppliedPolicyVersionId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, Integer> policyVersionById = policyIds.isEmpty()
            ? Map.of()
            : policyRepository.findAllById(policyIds).stream()
                .collect(Collectors.toMap(
                    ClassroomPolicyVersion::getId,
                    ClassroomPolicyVersion::getVersion
                ));

        List<AiRunHistoryView> items = runs.stream()
            .map(run -> view(
                run,
                proposalsByRun.getOrDefault(run.getId(), List.of()),
                artifactByRun.get(run.getId()),
                decisionByProposal,
                policyVersionById
            ))
            .toList();
        AiRun last = runs.getLast();
        return new AiRunHistoryPage(
            items,
            hasMore ? encode(new Cursor(last.getRequestedAt(), last.getId())) : null,
            hasMore
        );
    }

    private AiRunHistoryView view(
        AiRun run,
        List<AiProposal> proposals,
        AiArtifact artifact,
        Map<UUID, AiDecision> decisions,
        Map<UUID, Integer> policyVersions
    ) {
        Map<String, Object> validation = run.getValidationResult();
        String artifactSummary = summary(artifact);
        String severity = text(validation.get("overall_severity"));
        if (severity == null) {
            severity = text(validation.get("analysis_severity"));
        }
        if (severity == null && !proposals.isEmpty()) {
            severity = proposals.getFirst().getSeverity();
        }
        return new AiRunHistoryView(
            run.getId(),
            run.getAsyncOperationId(),
            featureType(run.getRunType()),
            run.getRunType(),
            run.getTriggerType(),
            run.getStatus(),
            run.getRequestedAt(),
            run.getCompletedAt(),
            run.getAnalysisPeriodFrom(),
            run.getAnalysisPeriodTo(),
            run.getPromptVersion(),
            pendingAsNull(run.getModelProvider()),
            pendingAsNull(run.getModelId()),
            artifact == null ? null : artifact.getSafetyStatus(),
            Boolean.TRUE.equals(validation.get("fallback_used")),
            text(validation.get("fallback_code")),
            severity,
            strings(validation.get("validation_codes")),
            evidenceCodes(artifact),
            ruleVersions(validation, proposals),
            proposals.stream()
                .map(proposal -> proposalView(
                    proposal,
                    artifactSummary,
                    decisions.get(proposal.getId()),
                    proposal.getAppliedPolicyVersionId() == null
                        ? null
                        : policyVersions.get(proposal.getAppliedPolicyVersionId())
                ))
                .toList()
        );
    }

    private AiRunHistoryView.Proposal proposalView(
        AiProposal proposal,
        String artifactSummary,
        AiDecision decision,
        Integer policyVersion
    ) {
        Map<String, Object> values = proposal.getProposedValues();
        Map<String, Map<String, Object>> allowedByAction = maps(
            proposal.getAllowedRanges().get("items")
        ).stream().collect(Collectors.toMap(
            item -> text(item.get("action_id")),
            Function.identity(),
            (first, ignored) -> first,
            LinkedHashMap::new
        ));
        List<AiRunHistoryView.ProposedValue> proposedValues = maps(values.get("items"))
            .stream()
            .map(item -> {
                Map<String, Object> allowed = allowedByAction.getOrDefault(
                    text(item.get("action_id")),
                    Map.of()
                );
                return new AiRunHistoryView.ProposedValue(
                    text(item.get("action_id")),
                    firstNonBlank(text(item.get("target_type")), text(allowed.get("target_type"))),
                    firstUuid(item.get("target_id"), allowed.get("target_id")),
                    firstNumber(item.get("current_value"), allowed.get("current_value")),
                    number(item.get("proposed_value")),
                    text(item.get("unit")),
                    text(item.get("label")),
                    text(item.get("rationale")),
                    text(item.get("expected_effect")),
                    strings(item.get("side_effects")),
                    strings(item.get("evidence_metric_codes")),
                    integer(item.get("priority"))
                );
            })
            .toList();
        List<AiRunHistoryView.AllowedRange> allowedRanges = maps(
            proposal.getAllowedRanges().get("items")
        ).stream()
            .map(item -> new AiRunHistoryView.AllowedRange(
                text(item.get("action_id")),
                text(item.get("target_type")),
                uuid(item.get("target_id")),
                number(item.get("current_value")),
                number(item.get("allowed_min_value")),
                number(item.get("allowed_max_value"))
            ))
            .toList();
        return new AiRunHistoryView.Proposal(
            proposal.getId(),
            proposal.getProposalType(),
            proposal.getSourceType(),
            effectiveStatus(proposal),
            proposal.getSeverity(),
            proposal.getMetricCode(),
            proposal.getDirection(),
            proposal.getDedupeKey(),
            truncate(
                firstNonBlank(artifactSummary, itemSummary(values), proposal.getProposalType()),
                1500
            ),
            text(values.get("context_type")),
            text(values.get("target_type")),
            uuid(values.get("target_id")),
            number(values.get("target_version")),
            proposedValues,
            allowedRanges,
            proposal.getCreatedAt(),
            proposal.getExpiresAt(),
            proposal.getStatusChangedAt(),
            decision == null ? null : decision.getDecision(),
            decision == null ? null : decision.getDecidedAt(),
            policyVersion,
            proposal.getAppliedPolicyVersionId(),
            proposal.getStateVersion()
        );
    }

    private String effectiveStatus(AiProposal proposal) {
        if (
            "AWAITING_TEACHER".equals(proposal.getStatus())
                && !proposal.getExpiresAt().isAfter(clock.instant())
        ) {
            return "EXPIRED";
        }
        return proposal.getStatus();
    }

    private static Map<String, String> ruleVersions(
        Map<String, Object> validation,
        List<AiProposal> proposals
    ) {
        Map<String, String> versions = new LinkedHashMap<>();
        copyVersion(validation, versions, "rule_version");
        copyVersion(validation, versions, "metric_version");
        copyVersion(validation, versions, "threshold_version");
        for (AiProposal proposal : proposals) {
            copyVersion(proposal.getProposedValues(), versions, "rule_version");
            copyVersion(proposal.getProposedValues(), versions, "metric_version");
            copyVersion(proposal.getProposedValues(), versions, "threshold_version");
        }
        return Map.copyOf(versions);
    }

    private static void copyVersion(
        Map<String, Object> source,
        Map<String, String> target,
        String key
    ) {
        String value = text(source.get(key));
        if (value != null) {
            target.putIfAbsent(key, value);
        }
    }

    private static List<String> evidenceCodes(AiArtifact artifact) {
        if (artifact == null) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        collectEvidence(artifact.getContent(), codes);
        return List.copyOf(codes);
    }

    private static void collectEvidence(Object value, Set<String> codes) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (EVIDENCE_KEYS.contains(key)) {
                    codes.addAll(strings(entry.getValue()));
                } else {
                    collectEvidence(entry.getValue(), codes);
                }
            }
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectEvidence(item, codes));
        }
    }

    private static String summary(AiArtifact artifact) {
        if (artifact == null) {
            return null;
        }
        Object output = artifact.getContent().get("output");
        if (output instanceof Map<?, ?> map) {
            return text(map.get("summary"));
        }
        return null;
    }

    private static String itemSummary(Map<String, Object> values) {
        List<Map<String, Object>> items = maps(values.get("items"));
        if (items.isEmpty()) {
            return null;
        }
        Map<String, Object> first = items.getFirst();
        return firstNonBlank(
            text(first.get("summary")),
            text(first.get("label")),
            text(first.get("text"))
        );
    }

    private static String featureType(String runType) {
        if ("TEACHER_CHAT".equals(runType)) {
            return "TEACHER_CHAT";
        }
        if ("ECONOMIC_EVENT_DRAFT".equals(runType)) {
            return "ECONOMIC_EVENT";
        }
        if ("QUIZ_DRAFT".equals(runType)) {
            return "QUIZ_DRAFT";
        }
        return "BALANCING";
    }

    private static String pendingAsNull(String value) {
        return "PENDING".equals(value) ? null : value;
    }

    private static int limit(Integer requested) {
        int resolved = requested == null ? DEFAULT_LIMIT : requested;
        if (resolved < 1 || resolved > MAXIMUM_LIMIT) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "limit must be between 1 and 100."
            );
        }
        return resolved;
    }

    private static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                Base64.getUrlDecoder().decode(encoded),
                StandardCharsets.UTF_8
            );
            int separator = decoded.lastIndexOf('|');
            if (separator < 1) {
                throw new IllegalArgumentException("separator is missing");
            }
            return new Cursor(
                Instant.parse(decoded.substring(0, separator)),
                UUID.fromString(decoded.substring(separator + 1))
            );
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "INVALID_CURSOR",
                "cursor is invalid."
            );
        }
    }

    private static String encode(Cursor cursor) {
        String raw = cursor.requestedAt() + "|" + cursor.runId();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                raw.forEach((key, nested) -> mapped.put(String.valueOf(key), nested));
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
            .filter(java.util.Objects::nonNull)
            .map(String::valueOf)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long firstNumber(Object first, Object second) {
        Long value = number(first);
        return value == null ? number(second) : value;
    }

    private static Integer integer(Object value) {
        Long number = number(value);
        return number == null ? null : Math.toIntExact(number);
    }

    private static UUID uuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID firstUuid(Object first, Object second) {
        UUID value = uuid(first);
        return value == null ? uuid(second) : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "AI proposal";
    }

    private static String truncate(String value, int maximumLength) {
        return value.substring(0, Math.min(value.length(), maximumLength));
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private record Cursor(Instant requestedAt, UUID runId) {
    }
}
