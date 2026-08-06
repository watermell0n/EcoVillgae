package com.b201.ecovillage.ai.infrastructure;

import java.time.Clock;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.b201.ecovillage.ai.contract.EconomicEventContract;

import tools.jackson.databind.json.JsonMapper;

@Component
public class FastApiEconomicEventClient implements EconomicEventAiClient {

    private static final String PATH = "/internal/v1/generations/economic-event";

    private final AiServiceProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final JsonMapper objectMapper;

    public FastApiEconomicEventClient(
        AiServiceProperties properties,
        Clock clock,
        JsonMapper objectMapper
    ) {
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .messageConverters(converters -> converters.add(
                0,
                new JacksonJsonHttpMessageConverter(objectMapper)
            ))
            .build();
    }

    @Override
    public EconomicEventContract.Response draft(EconomicEventContract.Request request) {
        if (!properties.isEnabled()) {
            throw new AiServiceCallException("AI_DISABLED", "The AI event service is disabled.");
        }
        RestClientException lastFailure = null;
        AiServiceErrorBody lastFailureDetail = AiServiceErrorBody.unknown();
        int attempts = properties.getMaxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (!clock.instant().isBefore(request.deadlineAt())) {
                throw new AiServiceCallException(
                    "AI_DEADLINE_EXCEEDED",
                    "The AI event deadline was exceeded."
                );
            }
            try {
                EconomicEventContract.Request attempted = request.withAttempt(attempt);
                EconomicEventContract.Response response = restClient.post()
                    .uri(PATH)
                    .headers(headers -> {
                        headers.setBearerAuth(properties.getToken());
                        headers.set("X-Correlation-Id", attempted.correlationId().toString());
                        headers.set("X-AI-Run-Id", attempted.aiRunId().toString());
                    })
                    .body(attempted)
                    .retrieve()
                    .body(EconomicEventContract.Response.class);
                if (response == null) {
                    throw new AiServiceCallException(
                        "AI_EMPTY_RESPONSE",
                        "The AI event service returned an empty response."
                    );
                }
                return response;
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                lastFailureDetail = AiServiceErrorBody.parse(objectMapper, exception);
                // 모델이 만든 문장이 안전 규칙에 걸린 경우는 같은 입력으로 다시 뽑으면 대개 통과한다.
                // 반대로 입력 계약 위반은 몇 번을 보내도 같은 결과이므로 즉시 포기한다.
                if (!lastFailureDetail.worthRetrying(exception.getStatusCode().value())) {
                    break;
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
                lastFailureDetail = AiServiceErrorBody.unknown();
            }
        }
        String code = lastFailure instanceof RestClientResponseException responseException
            ? "AI_HTTP_" + responseException.getStatusCode().value()
            : "AI_UNAVAILABLE";
        throw new AiServiceCallException(
            code,
            "The AI event service call failed: " + lastFailureDetail.describe(),
            lastFailureDetail.validationCodes(),
            lastFailure
        );
    }
}
