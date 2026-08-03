package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.repository.AiRunRepository;

@Component
public class ClassroomSetupPrecheckGuard {

    private static final Set<String> VALID_RUN_STATUSES = Set.of(
        "AWAITING_TEACHER",
        "ACKNOWLEDGED",
        "APPROVED",
        "COMPLETED"
    );

    private final AiRunRepository runRepository;

    public ClassroomSetupPrecheckGuard(AiRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public void verify(
        UUID classroomId,
        UUID preflightRunId,
        List<String> warningAcknowledgements
    ) {
        if (preflightRunId == null) {
            throw preflightRequired("Run the classroom setup preflight before opening access.");
        }
        AiRun run = runRepository.findByClassroomIdAndId(classroomId, preflightRunId)
            .orElseThrow(() -> preflightRequired("The classroom setup preflight was not found."));
        if (!"SETUP_PRECHECK".equals(run.getRunType()) || !VALID_RUN_STATUSES.contains(run.getStatus())) {
            throw preflightRequired("The classroom setup preflight is no longer valid.");
        }
        if (!"CLASSROOM_SETUP".equals(contextType(run.getInputScope()))) {
            throw preflightRequired("A CLASSROOM_SETUP preflight is required before first opening.");
        }

        String severity = String.valueOf(
            run.getValidationResult().getOrDefault("overall_severity", "BLOCK")
        );
        switch (severity) {
            case "PASS" -> {
                return;
            }
            case "BLOCK" -> throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PREFLIGHT_BLOCKED",
                "Resolve the blocking classroom setup checks before opening access."
            );
            case "WARN" -> {
                Set<String> required = warningCheckIds(run.getInputScope());
                if (required.isEmpty()) {
                    throw preflightRequired("The classroom setup warning details are invalid.");
                }
                Set<String> acknowledged = warningAcknowledgements == null
                    ? Set.of()
                    : warningAcknowledgements.stream().collect(Collectors.toUnmodifiableSet());
                if (acknowledged.containsAll(required)) {
                    return;
                }
                throw new ClassroomOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREFLIGHT_WARNING_ACKNOWLEDGEMENT_REQUIRED",
                    "Acknowledge every classroom setup warning before opening access."
                );
            }
            default -> throw preflightRequired("The classroom setup preflight result is invalid.");
        }
    }

    private static String contextType(Map<String, Object> inputScope) {
        Object contextValue = inputScope.get("context");
        if (!(contextValue instanceof Map<?, ?> context)) {
            return null;
        }
        Object value = context.get("context_type");
        return value == null ? null : value.toString();
    }

    private static Set<String> warningCheckIds(Map<String, Object> inputScope) {
        Object checksValue = inputScope.get("checks");
        if (!(checksValue instanceof List<?> checks)) {
            return Set.of();
        }
        return checks.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .filter(check -> "WARN".equals(String.valueOf(check.get("severity"))))
            .map(check -> check.get("check_id"))
            .filter(java.util.Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static ClassroomOperationException preflightRequired(String message) {
        return new ClassroomOperationException(
            HttpStatus.CONFLICT,
            "PREFLIGHT_CHECK_REQUIRED",
            message
        );
    }
}
