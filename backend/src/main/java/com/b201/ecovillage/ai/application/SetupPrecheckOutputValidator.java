package com.b201.ecovillage.ai.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;

@Component
public class SetupPrecheckOutputValidator {

    public void validate(
        SetupPrecheckContract.Request request,
        SetupPrecheckContract.Response response
    ) {
        require(Objects.equals(request.aiRunId(), response.aiRunId()), "AI_RUN_ID_CHANGED");
        require(SetupPrecheckContract.SCHEMA_VERSION.equals(response.schemaVersion()), "SCHEMA_VERSION_CHANGED");
        require(Objects.equals(request.promptVersion(), response.promptVersion()), "PROMPT_VERSION_CHANGED");
        require("PASSED".equals(response.safetyStatus()), "AI_SAFETY_REJECTED");
        require(response.output() != null, "OUTPUT_MISSING");
        require("REVIEW_REQUIRED".equals(response.output().reviewStatus()), "REVIEW_BOUNDARY_CHANGED");
        require(request.payload().context().contextType() == response.output().contextType(), "CONTEXT_CHANGED");

        SetupPrecheckContract.Severity expectedSeverity = request.payload().checks().stream()
            .map(SetupPrecheckContract.Check::severity)
            .max(java.util.Comparator.comparingInt(SetupPrecheckOutputValidator::priority))
            .orElse(SetupPrecheckContract.Severity.PASS);
        require(expectedSeverity == response.output().overallSeverity(), "SEVERITY_CHANGED");

        Map<String, SetupPrecheckContract.Candidate> candidates = new HashMap<>();
        request.payload().candidates().forEach(value -> candidates.put(value.actionId(), value));
        require(
            candidates.size() == response.output().candidateExplanations().size(),
            "CANDIDATE_COUNT_CHANGED"
        );
        Set<String> seenCandidates = new HashSet<>();
        for (SetupPrecheckContract.CandidateExplanation explanation
            : response.output().candidateExplanations()) {
            SetupPrecheckContract.Candidate expected = candidates.get(explanation.actionId());
            require(expected != null && seenCandidates.add(explanation.actionId()), "CANDIDATE_CHANGED");
            require(expected.targetType() == explanation.targetType(), "TARGET_CHANGED");
            require(Objects.equals(expected.label(), explanation.label()), "LABEL_CHANGED");
            require(expected.proposedValue() == explanation.proposedValue(), "PROPOSED_VALUE_CHANGED");
            require(expected.recommendedMinValue() == explanation.recommendedMinValue(), "RECOMMENDED_RANGE_CHANGED");
            require(expected.recommendedMaxValue() == explanation.recommendedMaxValue(), "RECOMMENDED_RANGE_CHANGED");
            require(expected.allowedMinValue() == explanation.allowedMinValue(), "ALLOWED_RANGE_CHANGED");
            require(expected.allowedMaxValue() == explanation.allowedMaxValue(), "ALLOWED_RANGE_CHANGED");
            require(expected.unit() == explanation.unit(), "UNIT_CHANGED");
            require(expected.reasonCodes().equals(explanation.evidenceReasonCodes()), "REASON_CODES_CHANGED");
        }

        Map<String, SetupPrecheckContract.Check> checks = new HashMap<>();
        request.payload().checks().forEach(value -> checks.put(value.checkId(), value));
        require(checks.size() == response.output().checkExplanations().size(), "CHECK_COUNT_CHANGED");
        Set<String> seenChecks = new HashSet<>();
        for (SetupPrecheckContract.CheckExplanation explanation
            : response.output().checkExplanations()) {
            SetupPrecheckContract.Check expected = checks.get(explanation.checkId());
            require(expected != null && seenChecks.add(explanation.checkId()), "CHECK_CHANGED");
            require(expected.severity() == explanation.severity(), "CHECK_SEVERITY_CHANGED");
            require(Objects.equals(expected.reasonCode(), explanation.reasonCode()), "CHECK_REASON_CHANGED");
            require(expected.subjectActionIds().equals(explanation.subjectActionIds()), "CHECK_SUBJECTS_CHANGED");
            if (expected.severity() == SetupPrecheckContract.Severity.PASS) {
                require(explanation.correctionGuidance() == null, "PASS_GUIDANCE_FORBIDDEN");
            } else {
                require(
                    explanation.correctionGuidance() != null
                        && !explanation.correctionGuidance().isBlank(),
                    "CORRECTION_GUIDANCE_REQUIRED"
                );
            }
        }
    }

    private static int priority(SetupPrecheckContract.Severity severity) {
        return switch (severity) {
            case PASS -> 0;
            case WARN -> 1;
            case BLOCK -> 2;
        };
    }

    private static void require(boolean condition, String code) {
        if (!condition) {
            throw new AiServiceCallException(
                "AI_OUTPUT_INVALID",
                "The AI output did not preserve the Spring rule result: " + code
            );
        }
    }
}
