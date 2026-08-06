package com.b201.ecovillage.ai.infrastructure;

import java.time.Clock;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.b201.ecovillage.ai.contract.InvestmentCompanyContract;

import tools.jackson.databind.json.JsonMapper;

@Component
public class FastApiInvestmentCompanyClient implements InvestmentCompanyAiClient {

    private static final String PATH = "/internal/v1/generations/investment-company";

    private final AiServiceProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    public FastApiInvestmentCompanyClient(
        AiServiceProperties properties,
        Clock clock,
        JsonMapper objectMapper
    ) {
        this.properties = properties;
        this.clock = clock;
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
    public InvestmentCompanyContract.Response create(InvestmentCompanyContract.Request request) {
        if (!properties.isEnabled()) {
            throw new AiServiceCallException("AI_DISABLED", "The AI company generator is disabled.");
        }
        RestClientException lastFailure = null;
        int attempts = Math.min(2, properties.getMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (!clock.instant().isBefore(request.deadlineAt())) {
                throw new AiServiceCallException(
                    "AI_DEADLINE_EXCEEDED",
                    "The AI company generation deadline was exceeded."
                );
            }
            try {
                InvestmentCompanyContract.Request attempted = request.withAttempt(attempt);
                InvestmentCompanyContract.Response response = restClient.post()
                    .uri(PATH)
                    .headers(headers -> {
                        headers.setBearerAuth(properties.getToken());
                        headers.set("X-Correlation-Id", attempted.correlationId().toString());
                        headers.set("X-AI-Run-Id", attempted.aiRunId().toString());
                    })
                    .body(attempted)
                    .retrieve()
                    .body(InvestmentCompanyContract.Response.class);
                if (response == null) {
                    throw new AiServiceCallException(
                        "AI_EMPTY_RESPONSE",
                        "The AI company generator returned an empty response."
                    );
                }
                return response;
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                // 4xx는 다시 보내도 같은 이유로 거절된다. 429만 잠시 뒤 다시 시도한다.
                if (exception.getStatusCode().is4xxClientError()
                    && exception.getStatusCode().value() != 429) {
                    break;
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
            }
        }
        String code = lastFailure instanceof RestClientResponseException responseException
            ? "AI_HTTP_" + responseException.getStatusCode().value()
            : "AI_UNAVAILABLE";
        throw new AiServiceCallException(code, "The AI company generator call failed.", lastFailure);
    }
}
