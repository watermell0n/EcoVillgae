package com.b201.ecovillage.ai.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 학급 투자 활동에 쓸 가상 기업을 새로 짓는 내부 AI 계약이다.
 *
 * <p>시나리오를 새로 만들 때마다 호출하므로 기업 이름이 매번 달라진다. 생성에 실패하면
 * 기본 기업 목록으로 만들기 때문에 투자 수업 자체는 멈추지 않는다.
 */
public final class InvestmentCompanyContract {

    public static final String SCHEMA_VERSION = "1.2";
    public static final String PROMPT_VERSION = "investment-company-v1";

    private InvestmentCompanyContract() {
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
                aiRunId, requestId, correlationId, schemaVersion, promptVersion, value,
                deadlineAt, locale, grade, payload
            );
        }
    }

    public record Payload(
        CourseScale courseScale,
        InvestmentMode investmentMode,
        int companyCount,
        List<String> usedNames,
        String teacherContext
    ) {
        public Payload {
            usedNames = List.copyOf(usedNames);
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
        InvestmentMode investmentMode,
        List<Company> companies
    ) {
        public Output {
            companies = List.copyOf(companies);
        }
    }

    public record Company(String symbol, String name, String profile) {
    }

    public enum CourseScale {
        ONE_SHOT,
        ONE_WEEK,
        ONE_MONTH,
        ONE_SEMESTER
    }

    public enum InvestmentMode {
        SHORT_TERM,
        LONG_TERM
    }
}
