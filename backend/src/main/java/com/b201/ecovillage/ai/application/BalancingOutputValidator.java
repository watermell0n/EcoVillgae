package com.b201.ecovillage.ai.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;

@Component
public class BalancingOutputValidator {

    public void validate(
        BalancingContract.Request request,
        BalancingContract.Response response
    ) {
        try {
            required(response != null && response.output() != null);
            required(request.aiRunId().equals(response.aiRunId()));
            required(BalancingContract.SCHEMA_VERSION.equals(response.schemaVersion()));
            required(BalancingContract.PROMPT_VERSION.equals(response.promptVersion()));
            required("PASSED".equals(response.safetyStatus()));
            BalancingContract.Output output = response.output();
            required("REVIEW_REQUIRED".equals(output.reviewStatus()));
            required(output.analysisMode() == request.analysisMode());
            required(output.triggerType() == request.triggerType());
            required(output.analysisSeverity() == request.payload().analysisSeverity());
            required(output.metricVersion().equals(request.payload().metricVersion()));
            required(output.thresholdVersion().equals(request.payload().thresholdVersion()));
            required(output.period().equals(request.payload().period()));
            required(java.util.Objects.equals(output.previousPeriod(), request.payload().previousPeriod()));

            Set<BalancingContract.MetricCode> evidence = availableEvidence(request);
            required(!output.evidenceMetricCodes().isEmpty());
            required(evidence.containsAll(output.evidenceMetricCodes()));
            for (BalancingContract.Cause cause : output.causes()) {
                required(!cause.evidenceMetricCodes().isEmpty());
                required(evidence.containsAll(cause.evidenceMetricCodes()));
            }
            Map<String, BalancingContract.AllowedAction> actions = new HashMap<>();
            request.payload().allowedActions().forEach(action -> actions.put(action.actionId(), action));
            Set<String> proposalIds = new HashSet<>();
            for (BalancingContract.Proposal proposal : output.proposals()) {
                required(proposalIds.add(proposal.actionId()));
                BalancingContract.AllowedAction action = actions.get(proposal.actionId());
                required(action != null);
                required(action.targetType() == proposal.targetType());
                required(java.util.Objects.equals(action.label(), proposal.label()));
                required(action.currentValue() == proposal.currentValue());
                required(action.minValue() == proposal.minValue());
                required(action.maxValue() == proposal.maxValue());
                required(action.unit() == proposal.unit());
                required(proposal.proposedValue() >= action.minValue());
                required(proposal.proposedValue() <= action.maxValue());
                required(!proposal.evidenceMetricCodes().isEmpty());
                required(evidence.containsAll(proposal.evidenceMetricCodes()));
            }
            if (request.payload().analysisSeverity() == BalancingContract.AnalysisSeverity.NORMAL) {
                required(output.proposals().isEmpty());
            }
        } catch (RuntimeException exception) {
            if (exception instanceof AiServiceCallException callException) {
                throw callException;
            }
            throw new AiServiceCallException(
                "AI_OUTPUT_INVALID",
                "The AI balancing output changed trusted Spring values.",
                exception
            );
        }
    }

    private Set<BalancingContract.MetricCode> availableEvidence(
        BalancingContract.Request request
    ) {
        Set<BalancingContract.MetricCode> result = new HashSet<>();
        for (BalancingContract.Metric metric : request.payload().metrics()) {
            if (metric.availability() == BalancingContract.Availability.AVAILABLE) {
                result.add(metric.code());
            }
        }
        return result;
    }

    private static void required(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("AI output contract mismatch");
        }
    }
}
