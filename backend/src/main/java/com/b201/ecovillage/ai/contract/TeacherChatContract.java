package com.b201.ecovillage.ai.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeacherChatContract {

    public static final String SCHEMA_VERSION = "1.2";
    public static final String PROMPT_VERSION = "teacher-chat-v1";
    public static final String LOCALE = "ko-KR";

    private TeacherChatContract() {
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
                payload
            );
        }
    }

    public record Payload(
        String question,
        Period period,
        List<Fact> facts,
        List<PolicyChange> recentPolicyChanges
    ) {
        public Payload {
            facts = List.copyOf(facts);
            recentPolicyChanges = List.copyOf(recentPolicyChanges);
        }
    }

    public record Period(Instant from, Instant to, Instant dataAsOf) {
    }

    /**
     * value는 정수 또는 문자열만 허용한다. FastAPI 계약이 두 형태만 받는다.
     */
    public record Fact(String factId, String label, Object value, FactUnit unit) {
    }

    public record PolicyChange(String factId, Instant changedAt, String summary) {
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
        ResponseMode responseMode,
        String answer,
        List<String> evidenceFactIds,
        List<Cause> causes,
        List<Recommendation> recommendations,
        List<LimitationCode> limitationCodes,
        Period period,
        Instant dataAsOf
    ) {
        public Output {
            evidenceFactIds = List.copyOf(evidenceFactIds);
            causes = List.copyOf(causes);
            recommendations = List.copyOf(recommendations);
            limitationCodes = List.copyOf(limitationCodes);
        }
    }

    public record Cause(String text, List<String> evidenceFactIds) {
        public Cause {
            evidenceFactIds = List.copyOf(evidenceFactIds);
        }
    }

    public record Recommendation(
        String text,
        List<String> evidenceFactIds,
        List<String> sideEffects
    ) {
        public Recommendation {
            evidenceFactIds = List.copyOf(evidenceFactIds);
            sideEffects = List.copyOf(sideEffects);
        }
    }

    public enum ResponseMode {
        ANSWER,
        LIMITED
    }

    public enum LimitationCode {
        PERSONAL_ANALYSIS_NOT_SUPPORTED,
        REAL_FINANCIAL_ADVICE_NOT_SUPPORTED,
        FUTURE_PREDICTION_NOT_SUPPORTED,
        READ_ONLY_BOUNDARY,
        PROMPT_INJECTION_BLOCKED,
        INSUFFICIENT_EVIDENCE
    }

    public enum FactUnit {
        AMOUNT,
        COUNT,
        RATE_BPS,
        STATUS,
        TEXT
    }
}
