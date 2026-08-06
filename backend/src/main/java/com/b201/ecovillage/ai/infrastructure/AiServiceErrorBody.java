package com.b201.ecovillage.ai.infrastructure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 서비스가 실패 응답에 담아 준 사유다.
 *
 * <p>지금까지는 상태 코드만 남기고 본문을 버려서, 실패하면 기록에 "AI_HTTP_422"만 남고 왜
 * 걸렸는지 알 수 없었다. 사유를 읽어 두면 다시 시도할 값어치가 있는 실패인지도 판단할 수 있다.
 */
record AiServiceErrorBody(String code, List<String> validationCodes, boolean retryable) {

    /** 모델이 만든 문장이 규칙에 걸린 경우다. 같은 입력이라도 다시 뽑으면 대개 통과한다. */
    private static final List<String> RETRYABLE_CODES = List.of(
        "AI_OUTPUT_SCHEMA_INVALID",
        "AI_OUTPUT_SAFETY_REJECTED"
    );

    static AiServiceErrorBody unknown() {
        return new AiServiceErrorBody("", List.of(), false);
    }

    static AiServiceErrorBody parse(JsonMapper objectMapper, RestClientResponseException exception) {
        try {
            JsonNode error = objectMapper
                .readTree(exception.getResponseBodyAsString())
                .path("error");
            List<String> codes = new ArrayList<>();
            error.path("validation_codes").forEach(node -> codes.add(node.asString()));
            return new AiServiceErrorBody(
                error.path("code").asString(""),
                List.copyOf(codes),
                error.path("retryable").asBoolean(false)
            );
        } catch (RuntimeException ignored) {
            // 본문을 읽지 못해도 호출 자체는 실패로 처리해야 하므로 사유 없이 이어 간다.
            return unknown();
        }
    }

    /**
     * 다시 보낼 값어치가 있는 실패인지 판단한다.
     *
     * <p>429와 서버 오류는 일시적이다. 출력 검증 실패는 모델이 매번 다른 문장을 만들어 내므로
     * 다시 뽑으면 통과할 수 있다. 입력 계약 위반은 같은 요청을 몇 번 보내도 결과가 같다.
     */
    boolean worthRetrying(int status) {
        if (status == 429 || status >= 500) {
            return true;
        }
        if (status < 400) {
            return false;
        }
        return retryable || RETRYABLE_CODES.contains(code);
    }

    String describe() {
        if (code.isBlank() && validationCodes.isEmpty()) {
            return "사유를 확인할 수 없습니다.";
        }
        return validationCodes.isEmpty()
            ? code
            : code + " " + String.join(",", validationCodes);
    }
}
