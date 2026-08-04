package com.b201.ecovillage.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.b201.ecovillage.ai.contract.BalancingContract;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

public class FastApiBalancingClientTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    private HttpServer server;
    private AiServiceProperties properties;
    private AtomicInteger calls;
    private AtomicReference<String> lastAuthorization;
    private AtomicReference<String> lastBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        properties = new AiServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("balancing-contract-token");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setMaxAttempts(2);
        calls = new AtomicInteger();
        lastAuthorization = new AtomicReference<>();
        lastBody = new AtomicReference<>();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsSnakeCaseV12PayloadAndRetriesOneTransientFailure() {
        BalancingContract.Request request = request();
        server.createContext("/internal/v1/generations/balancing", exchange -> {
            capture(exchange);
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 503, "{}");
            } else {
                respond(exchange, 200, responseJson(request));
            }
        });
        server.start();

        BalancingContract.Response response = client().explain(request);

        assertEquals(2, calls.get());
        assertEquals("Bearer balancing-contract-token", lastAuthorization.get());
        assertTrue(lastBody.get().contains("\"metric_version\":\"economic-metrics-v1\""));
        assertTrue(lastBody.get().contains("\"analysis_mode\":\"ON_DEMAND\""));
        assertTrue(lastBody.get().contains("\"attempt\":2"));
        assertEquals(request.aiRunId(), response.aiRunId());
        assertEquals(BalancingContract.AnalysisSeverity.WARN, response.output().analysisSeverity());
    }

    @Test
    void doesNotRetryNonRateLimitedClientErrors() {
        server.createContext("/internal/v1/generations/balancing", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 422, "{}");
        });
        server.start();

        AiServiceCallException exception = assertThrows(
            AiServiceCallException.class,
            () -> client().explain(request())
        );

        assertEquals("AI_HTTP_422", exception.getCode());
        assertEquals(1, calls.get());
    }

    private FastApiBalancingClient client() {
        return new FastApiBalancingClient(
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build()
        );
    }

    private void capture(HttpExchange exchange) throws IOException {
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static BalancingContract.Request request() {
        BalancingContract.Period current = new BalancingContract.Period(
            Instant.parse("2026-07-25T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            NOW
        );
        BalancingContract.Period previous = new BalancingContract.Period(
            Instant.parse("2026-07-18T00:00:00Z"),
            Instant.parse("2026-07-25T00:00:00Z"),
            NOW
        );
        UUID targetId = UUID.randomUUID();
        return new BalancingContract.Request(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            BalancingContract.SCHEMA_VERSION,
            BalancingContract.PROMPT_VERSION,
            1,
            NOW.plusSeconds(10),
            "ko-KR",
            5,
            BalancingContract.AnalysisMode.ON_DEMAND,
            BalancingContract.TriggerType.TEACHER_REQUEST,
            new BalancingContract.Payload(
                BalancingContract.METRIC_VERSION,
                BalancingContract.THRESHOLD_VERSION,
                current,
                previous,
                BalancingContract.AnalysisSeverity.WARN,
                new BalancingContract.Sample(5, 10, BalancingContract.SampleStatus.SUFFICIENT),
                List.of(new BalancingContract.Metric(
                    BalancingContract.MetricCode.CONSUMPTION_RATE_BPS,
                    8500L,
                    8200L,
                    BalancingContract.Availability.AVAILABLE,
                    null,
                    BalancingContract.ValueUnit.RATE_BPS,
                    BalancingContract.MetricState.WARN,
                    BalancingContract.Direction.HIGH,
                    null
                )),
                List.of(new BalancingContract.AllowedAction(
                    "product-price-1",
                    BalancingContract.TargetType.PRODUCT_PRICE,
                    targetId,
                    100,
                    90,
                    110,
                    BalancingContract.ValueUnit.AMOUNT,
                    "기본 상품 가격"
                ))
            )
        );
    }

    private static String responseJson(BalancingContract.Request request) {
        return """
            {
              "ai_run_id": "%s",
              "schema_version": "1.2",
              "prompt_version": "balancing-v1",
              "provider": "FAKE",
              "model_id": "fake-model",
              "safety_status": "PASSED",
              "validation_codes": [],
              "usage": {"input_tokens": 10, "output_tokens": 5},
              "output": {
                "review_status": "REVIEW_REQUIRED",
                "analysis_mode": "ON_DEMAND",
                "trigger_type": "TEACHER_REQUEST",
                "analysis_severity": "WARN",
                "metric_version": "economic-metrics-v1",
                "threshold_version": "economic-thresholds-v1",
                "period": {"from": "2026-07-25T00:00:00Z", "to": "2026-08-01T00:00:00Z", "data_as_of": "2026-08-01T00:00:00Z"},
                "previous_period": {"from": "2026-07-18T00:00:00Z", "to": "2026-07-25T00:00:00Z", "data_as_of": "2026-08-01T00:00:00Z"},
                "summary": "소비 비율이 연속으로 높습니다.",
                "evidence_metric_codes": ["CONSUMPTION_RATE_BPS"],
                "causes": [{"text": "소비 비율이 높습니다.", "evidence_metric_codes": ["CONSUMPTION_RATE_BPS"]}],
                "proposals": [{
                  "action_id": "product-price-1",
                  "target_type": "PRODUCT_PRICE",
                  "label": "기본 상품 가격",
                  "current_value": 100,
                  "min_value": 90,
                  "max_value": 110,
                  "unit": "AMOUNT",
                  "proposed_value": 105,
                  "evidence_metric_codes": ["CONSUMPTION_RATE_BPS"],
                  "rationale": "허용 범위 안에서 조정합니다.",
                  "expected_effect": "소비 속도를 낮춥니다.",
                  "side_effects": ["구매 접근성이 낮아질 수 있습니다."],
                  "priority": 1
                }]
              }
            }
            """.formatted(request.aiRunId());
    }
}
