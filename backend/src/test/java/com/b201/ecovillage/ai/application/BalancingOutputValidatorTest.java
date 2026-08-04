package com.b201.ecovillage.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.FastApiBalancingClientTest;

class BalancingOutputValidatorTest {

    private final BalancingFallbackFactory fallbackFactory = new BalancingFallbackFactory();
    private final BalancingOutputValidator validator = new BalancingOutputValidator();

    @Test
    void rejectsAProposedValueOutsideTheSpringAllowedRange() {
        BalancingContract.Request request = FastApiBalancingClientTest.request();
        BalancingContract.Response base = fallbackFactory.create(request, "TEST");
        BalancingContract.Proposal current = base.output().proposals().getFirst();
        List<BalancingContract.Proposal> changed = new ArrayList<>(base.output().proposals());
        changed.set(0, new BalancingContract.Proposal(
            current.actionId(),
            current.targetType(),
            current.label(),
            current.currentValue(),
            current.minValue(),
            current.maxValue(),
            current.unit(),
            current.maxValue() + 1,
            current.evidenceMetricCodes(),
            current.rationale(),
            current.expectedEffect(),
            current.sideEffects(),
            current.priority()
        ));
        BalancingContract.Output output = new BalancingContract.Output(
            base.output().reviewStatus(),
            base.output().analysisMode(),
            base.output().triggerType(),
            base.output().analysisSeverity(),
            base.output().metricVersion(),
            base.output().thresholdVersion(),
            base.output().period(),
            base.output().previousPeriod(),
            base.output().summary(),
            base.output().evidenceMetricCodes(),
            base.output().causes(),
            changed
        );
        BalancingContract.Response tampered = new BalancingContract.Response(
            base.aiRunId(),
            base.schemaVersion(),
            base.promptVersion(),
            base.provider(),
            base.modelId(),
            base.safetyStatus(),
            List.of(),
            base.usage(),
            output
        );

        AiServiceCallException exception = assertThrows(
            AiServiceCallException.class,
            () -> validator.validate(request, tampered)
        );

        assertEquals("AI_OUTPUT_INVALID", exception.getCode());
    }
}
