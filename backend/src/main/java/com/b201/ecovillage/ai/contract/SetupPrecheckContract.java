package com.b201.ecovillage.ai.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SetupPrecheckContract {

    public static final String SCHEMA_VERSION = "1.2";
    public static final String PROMPT_VERSION = "setup-precheck-v1";

    private SetupPrecheckContract() {
    }

    public record Request(
        UUID aiRunId,
        UUID requestId,
        UUID correlationId,
        String schemaVersion,
        String promptVersion,
        int attempt,
        Instant deadlineAt,
        String locale,
        int grade,
        AnalysisMode analysisMode,
        TriggerType triggerType,
        Payload payload
    ) {
        public Request withAttempt(int value) {
            return new Request(
                aiRunId,
                requestId,
                correlationId,
                schemaVersion,
                promptVersion,
                value,
                deadlineAt,
                locale,
                grade,
                analysisMode,
                triggerType,
                payload
            );
        }
    }

    public record Payload(
        Context context,
        List<Candidate> candidates,
        List<Check> checks
    ) {
        public Payload {
            candidates = List.copyOf(candidates);
            checks = List.copyOf(checks);
        }
    }

    public record Context(
        ContextType contextType,
        CourseScale courseScale,
        int expectedStudents,
        int operationPeriodDays,
        List<String> activeModules,
        String moduleCode,
        UUID moduleId,
        QuizMode quizMode
    ) {
        public Context {
            activeModules = List.copyOf(activeModules);
        }
    }

    public record Candidate(
        String actionId,
        TargetType targetType,
        UUID targetId,
        long proposedValue,
        long recommendedMinValue,
        long recommendedMaxValue,
        long allowedMinValue,
        long allowedMaxValue,
        ValueUnit unit,
        List<String> reasonCodes,
        String label
    ) {
        public Candidate {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

    public record Check(
        String checkId,
        Severity severity,
        String reasonCode,
        List<String> subjectActionIds,
        String message
    ) {
        public Check {
            subjectActionIds = List.copyOf(subjectActionIds);
        }
    }

    public record Response(
        UUID aiRunId,
        String schemaVersion,
        String promptVersion,
        String provider,
        String modelId,
        String safetyStatus,
        List<String> validationCodes,
        Usage usage,
        Output output
    ) {
        public Response {
            validationCodes = List.copyOf(validationCodes);
        }
    }

    public record Usage(int inputTokens, int outputTokens) {
    }

    public record Output(
        String reviewStatus,
        ContextType contextType,
        Severity overallSeverity,
        String summary,
        List<CandidateExplanation> candidateExplanations,
        List<CheckExplanation> checkExplanations
    ) {
        public Output {
            candidateExplanations = List.copyOf(candidateExplanations);
            checkExplanations = List.copyOf(checkExplanations);
        }
    }

    public record CandidateExplanation(
        String actionId,
        TargetType targetType,
        String label,
        long proposedValue,
        long recommendedMinValue,
        long recommendedMaxValue,
        long allowedMinValue,
        long allowedMaxValue,
        ValueUnit unit,
        List<String> evidenceReasonCodes,
        String rationale,
        List<String> cautions
    ) {
        public CandidateExplanation {
            evidenceReasonCodes = List.copyOf(evidenceReasonCodes);
            cautions = List.copyOf(cautions);
        }
    }

    public record CheckExplanation(
        String checkId,
        Severity severity,
        String reasonCode,
        List<String> subjectActionIds,
        String explanation,
        String correctionGuidance
    ) {
        public CheckExplanation {
            subjectActionIds = List.copyOf(subjectActionIds);
        }
    }

    public enum AnalysisMode {
        SETUP_PRECHECK
    }

    public enum TriggerType {
        TEACHER_REQUEST
    }

    public enum ContextType {
        CLASSROOM_SETUP,
        MODULE_ACTIVATION,
        PRODUCT_DRAFT,
        QUIZ_SESSION_DRAFT
    }

    public enum CourseScale {
        ONE_SHOT,
        ONE_WEEK,
        ONE_MONTH,
        ONE_SEMESTER
    }

    public enum TargetType {
        ROLE_SALARY,
        INCOME_TAX_RATE,
        DEPOSIT_RATE,
        SAVINGS_RATE,
        LOAN_RATE,
        PRODUCT_PRICE,
        GAME_REWARD,
        INVESTMENT_CONDITION
    }

    public enum ValueUnit {
        AMOUNT,
        COUNT,
        RATE_BPS
    }

    public enum Severity {
        PASS,
        WARN,
        BLOCK
    }

    public enum QuizMode {
        GAME,
        TEST
    }
}
