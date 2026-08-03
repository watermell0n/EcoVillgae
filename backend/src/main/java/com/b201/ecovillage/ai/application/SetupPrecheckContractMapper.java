package com.b201.ecovillage.ai.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.ruleengine.domain.RuleCandidate;
import com.b201.ecovillage.ruleengine.domain.RuleCheck;
import com.b201.ecovillage.ruleengine.domain.RuleEngineRequest;
import com.b201.ecovillage.ruleengine.domain.RuleEngineResult;

@Component
public class SetupPrecheckContractMapper {

    public SetupPrecheckContract.Request toRequest(
        UUID aiRunId,
        UUID requestId,
        UUID correlationId,
        Instant deadlineAt,
        Classroom classroom,
        RuleEngineRequest ruleRequest,
        RuleEngineResult result
    ) {
        return new SetupPrecheckContract.Request(
            aiRunId,
            requestId,
            correlationId,
            SetupPrecheckContract.SCHEMA_VERSION,
            SetupPrecheckContract.PROMPT_VERSION,
            1,
            deadlineAt,
            "ko-KR",
            classroom.getGrade(),
            SetupPrecheckContract.AnalysisMode.SETUP_PRECHECK,
            SetupPrecheckContract.TriggerType.TEACHER_REQUEST,
            new SetupPrecheckContract.Payload(
                context(classroom, ruleRequest),
                result.candidates().stream().map(this::candidate).toList(),
                result.checks().stream().map(this::check).toList()
            )
        );
    }

    private SetupPrecheckContract.Context context(
        Classroom classroom,
        RuleEngineRequest request
    ) {
        RuleEngineRequest.ModuleActivation activation = request.moduleActivation();
        return new SetupPrecheckContract.Context(
            SetupPrecheckContract.ContextType.valueOf(request.contextType().name()),
            courseScale(classroom.getCourseSize()),
            Math.max(1, request.activeStudentCount()),
            operationDays(request.operationStartsOn(), request.operationEndsOn()),
            request.activeModules().stream().sorted(Comparator.naturalOrder()).toList(),
            activation == null ? null : activation.moduleCode(),
            activation == null ? null : activation.targetId(),
            request.quizDraft() == null
                ? null
                : SetupPrecheckContract.QuizMode.valueOf(request.quizDraft().mode().name())
        );
    }

    private SetupPrecheckContract.Candidate candidate(RuleCandidate candidate) {
        return new SetupPrecheckContract.Candidate(
            candidate.actionId(),
            SetupPrecheckContract.TargetType.valueOf(candidate.targetType().name()),
            candidate.targetId(),
            candidate.proposedValue(),
            candidate.recommendedMinValue(),
            candidate.recommendedMaxValue(),
            candidate.allowedMinValue(),
            candidate.allowedMaxValue(),
            switch (candidate.unit()) {
                case AMOUNT -> SetupPrecheckContract.ValueUnit.AMOUNT;
                case BPS -> SetupPrecheckContract.ValueUnit.RATE_BPS;
            },
            candidate.reasonCodes(),
            candidate.label()
        );
    }

    private SetupPrecheckContract.Check check(RuleCheck check) {
        return new SetupPrecheckContract.Check(
            check.checkId(),
            SetupPrecheckContract.Severity.valueOf(check.severity().name()),
            check.reasonCode(),
            check.subjectActionIds(),
            check.message()
        );
    }

    private static SetupPrecheckContract.CourseScale courseScale(CourseSize size) {
        return switch (size) {
            case ONE_SESSION -> SetupPrecheckContract.CourseScale.ONE_SHOT;
            case ONE_WEEK -> SetupPrecheckContract.CourseScale.ONE_WEEK;
            case ONE_MONTH -> SetupPrecheckContract.CourseScale.ONE_MONTH;
            case SEMESTER -> SetupPrecheckContract.CourseScale.ONE_SEMESTER;
        };
    }

    private static int operationDays(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 1;
        }
        return Math.toIntExact(Math.min(365L, ChronoUnit.DAYS.between(from, to) + 1L));
    }
}
