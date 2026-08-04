package com.b201.ecovillage.ai.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;

@Component
public class SetupPrecheckFallbackFactory {

    public SetupPrecheckContract.Response create(
        SetupPrecheckContract.Request request,
        String ruleVersion,
        String failureCode
    ) {
        SetupPrecheckContract.Severity severity = request.payload().checks().stream()
            .map(SetupPrecheckContract.Check::severity)
            .max(java.util.Comparator.comparingInt(SetupPrecheckFallbackFactory::priority))
            .orElse(SetupPrecheckContract.Severity.PASS);
        List<String> warnedActions = request.payload().checks().stream()
            .filter(check -> check.severity() != SetupPrecheckContract.Severity.PASS)
            .flatMap(check -> check.subjectActionIds().stream())
            .distinct()
            .toList();
        List<SetupPrecheckContract.CandidateExplanation> candidates = request.payload()
            .candidates()
            .stream()
            .map(candidate -> new SetupPrecheckContract.CandidateExplanation(
                candidate.actionId(),
                candidate.targetType(),
                candidate.label(),
                candidate.proposedValue(),
                candidate.recommendedMinValue(),
                candidate.recommendedMaxValue(),
                candidate.allowedMinValue(),
                candidate.allowedMaxValue(),
                candidate.unit(),
                candidate.reasonCodes(),
                "Spring 규칙 엔진 " + ruleVersion + "이 계산한 추천값입니다.",
                warnedActions.contains(candidate.actionId())
                    ? List.of("관련 경고를 확인한 뒤 값을 확정해 주세요.")
                    : List.of()
            ))
            .toList();
        List<SetupPrecheckContract.CheckExplanation> checks = request.payload()
            .checks()
            .stream()
            .map(check -> new SetupPrecheckContract.CheckExplanation(
                check.checkId(),
                check.severity(),
                check.reasonCode(),
                check.subjectActionIds(),
                check.message(),
                check.severity() == SetupPrecheckContract.Severity.PASS
                    ? null
                    : "점검 메시지와 허용 범위를 확인하고 설정을 수정하거나 승인해 주세요."
            ))
            .toList();
        List<String> validationCodes = new ArrayList<>();
        validationCodes.add("SPRING_RULE_FALLBACK_USED");
        validationCodes.add(failureCode);
        return new SetupPrecheckContract.Response(
            request.aiRunId(),
            SetupPrecheckContract.SCHEMA_VERSION,
            SetupPrecheckContract.PROMPT_VERSION,
            "SPRING_FALLBACK",
            ruleVersion,
            "PASSED",
            validationCodes,
            new SetupPrecheckContract.Usage(0, 0),
            new SetupPrecheckContract.Output(
                "REVIEW_REQUIRED",
                request.payload().context().contextType(),
                severity,
                summary(severity),
                candidates,
                checks
            )
        );
    }

    private static String summary(SetupPrecheckContract.Severity severity) {
        return switch (severity) {
            case PASS -> "규칙 기반 시작 전 점검을 통과했습니다. 교사가 값을 확인해 주세요.";
            case WARN -> "진행 가능한 경고가 있습니다. 교사가 경고와 추천값을 확인해 주세요.";
            case BLOCK -> "적용 전에 수정해야 하는 항목이 있습니다. 차단 사유를 확인해 주세요.";
        };
    }

    private static int priority(SetupPrecheckContract.Severity severity) {
        return switch (severity) {
            case PASS -> 0;
            case WARN -> 1;
            case BLOCK -> 2;
        };
    }
}
