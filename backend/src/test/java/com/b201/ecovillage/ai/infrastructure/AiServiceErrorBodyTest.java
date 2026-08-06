package com.b201.ecovillage.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.json.JsonMapper;

/**
 * 실패 사유를 읽어 두고, 다시 뽑으면 통과할 실패만 재시도하는지 확인한다.
 *
 * <p>본문은 실제 AI 서비스가 돌려준 모양 그대로 쓴다.
 */
class AiServiceErrorBodyTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private RestClientResponseException response(int status, String body) {
        return new RestClientResponseException(
            "call failed",
            HttpStatus.valueOf(status),
            HttpStatus.valueOf(status).getReasonPhrase(),
            null,
            body.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
    }

    @Test
    @DisplayName("교사 입력이 계약을 어긴 실패는 다시 보내도 같은 결과라 재시도하지 않는다")
    void doesNotRetryInputContractFailures() {
        AiServiceErrorBody body = AiServiceErrorBody.parse(objectMapper, response(422, """
            {"error":{"code":"AI_INPUT_SCHEMA_INVALID",
             "message":"투자 이야기의 다음 결과는 구조화된 내부 방향으로만 전달할 수 있습니다.",
             "retryable":false,
             "validation_codes":["INVESTMENT_FUTURE_INFORMATION_INPUT"]}}
            """));

        assertFalse(body.worthRetrying(422));
        assertEquals(List.of("INVESTMENT_FUTURE_INFORMATION_INPUT"), body.validationCodes());
        assertTrue(body.describe().contains("INVESTMENT_FUTURE_INFORMATION_INPUT"));
    }

    @Test
    @DisplayName("모델 문장이 안전 규칙에 걸린 실패는 다시 뽑으면 통과할 수 있어 재시도한다")
    void retriesGeneratedContentFailures() {
        AiServiceErrorBody rejected = AiServiceErrorBody.parse(objectMapper, response(422, """
            {"error":{"code":"AI_OUTPUT_SAFETY_REJECTED","retryable":false,
             "validation_codes":["INVESTMENT_TRADING_ADVICE"]}}
            """));
        AiServiceErrorBody malformed = AiServiceErrorBody.parse(objectMapper, response(422, """
            {"error":{"code":"AI_OUTPUT_SCHEMA_INVALID","retryable":false,
             "validation_codes":["INVESTMENT_MODE_CHOICES_MISMATCH"]}}
            """));

        assertTrue(rejected.worthRetrying(422));
        assertTrue(malformed.worthRetrying(422));
    }

    @Test
    @DisplayName("서비스가 재시도해도 된다고 알려 주면 그대로 따른다")
    void honoursTheServiceRetryHint() {
        AiServiceErrorBody body = AiServiceErrorBody.parse(objectMapper, response(503, """
            {"error":{"code":"AI_PROVIDER_UNAVAILABLE","retryable":true,
             "validation_codes":["PROVIDER_UNAVAILABLE"]}}
            """));

        assertTrue(body.worthRetrying(503));
    }

    @Test
    @DisplayName("호출 제한과 서버 오류는 사유를 못 읽어도 재시도한다")
    void retriesTransientFailuresWithoutABody() {
        AiServiceErrorBody body = AiServiceErrorBody.parse(objectMapper, response(429, "not json"));

        assertTrue(body.worthRetrying(429));
        assertTrue(body.worthRetrying(500));
        assertFalse(body.worthRetrying(422));
        assertEquals("사유를 확인할 수 없습니다.", body.describe());
    }
}
