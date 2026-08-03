package com.b201.ecovillage.ai.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.BalancingContract;

@Component
public class BalancingFallbackFactory {

    public BalancingContract.Response create(
        BalancingContract.Request request,
        String fallbackCode
    ) {
        List<BalancingContract.Metric> evidenceMetrics = request.payload().metrics().stream()
            .filter(metric -> metric.availability() == BalancingContract.Availability.AVAILABLE)
            .filter(metric -> metric.state() != BalancingContract.MetricState.NORMAL)
            .toList();
        if (evidenceMetrics.isEmpty()) {
            evidenceMetrics = request.payload().metrics().stream()
                .filter(metric -> metric.availability() == BalancingContract.Availability.AVAILABLE)
                .toList();
        }
        List<BalancingContract.MetricCode> evidence = evidenceMetrics.stream()
            .map(BalancingContract.Metric::code)
            .distinct()
            .toList();
        List<BalancingContract.Cause> causes = evidenceMetrics.stream()
            .map(metric -> new BalancingContract.Cause(
                metric.code().name() + " 지표를 Spring 계산 결과로 확인했습니다.",
                List.of(metric.code())
            ))
            .toList();
        List<BalancingContract.Proposal> proposals =
            request.payload().analysisSeverity() == BalancingContract.AnalysisSeverity.NORMAL
                ? List.of()
                : request.payload().allowedActions().stream().limit(20)
                    .map(action -> new BalancingContract.Proposal(
                        action.actionId(),
                        action.targetType(),
                        action.label(),
                        action.currentValue(),
                        action.minValue(),
                        action.maxValue(),
                        action.unit(),
                        action.currentValue(),
                        evidence,
                        "AI 서비스 장애로 현재 값을 유지하는 안전 제안을 제공합니다.",
                        "교사가 원본 지표와 허용 범위를 검토할 수 있습니다.",
                        List.of("자동 적용되지 않으며 교사 승인이 필요합니다."),
                        1
                    ))
                    .toList();
        return new BalancingContract.Response(
            request.aiRunId(),
            BalancingContract.SCHEMA_VERSION,
            BalancingContract.PROMPT_VERSION,
            "SPRING_FALLBACK",
            "deterministic-balancing-fallback",
            "PASSED",
            List.of(fallbackCode),
            new BalancingContract.Usage(0, 0),
            new BalancingContract.Output(
                "REVIEW_REQUIRED",
                request.analysisMode(),
                request.triggerType(),
                request.payload().analysisSeverity(),
                request.payload().metricVersion(),
                request.payload().thresholdVersion(),
                request.payload().period(),
                request.payload().previousPeriod(),
                "AI 설명을 사용할 수 없어 Spring이 계산한 지표를 그대로 보존했습니다.",
                evidence,
                causes,
                proposals
            )
        );
    }
}
