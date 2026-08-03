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

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class FastApiSetupPrecheckClientTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    private HttpServer server;
    private AiServiceProperties properties;
    private AtomicInteger calls;
    private AtomicReference<String> lastAuthorization;
    private AtomicReference<String> lastRunId;
    private AtomicReference<String> lastCorrelationId;
    private AtomicReference<String> lastBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        properties = new AiServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("contract-token");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setMaxAttempts(2);
        calls = new AtomicInteger();
        lastAuthorization = new AtomicReference<>();
        lastRunId = new AtomicReference<>();
        lastCorrelationId = new AtomicReference<>();
        lastBody = new AtomicReference<>();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsV12HeadersAndRetriesOnlyOnce() {
        SetupPrecheckContract.Request request = request();
        server.createContext("/internal/v1/generations/balancing", exchange -> {
            capture(exchange);
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 503, "{}");
            } else {
                respond(exchange, 200, responseJson(request));
            }
        });
        server.start();

        SetupPrecheckContract.Response response = client().explain(request);

        assertEquals(2, calls.get());
        assertEquals("Bearer contract-token", lastAuthorization.get());
        assertEquals(request.aiRunId().toString(), lastRunId.get());
        assertEquals(request.correlationId().toString(), lastCorrelationId.get());
        assertTrue(lastBody.get().contains("\"schema_version\":\"1.2\""));
        assertTrue(lastBody.get().contains("\"attempt\":2"));
        assertEquals(request.aiRunId(), response.aiRunId());
        assertEquals("FAKE", response.provider());
    }

    @Test
    void mapsAuthorizationFailureWithoutRetrying() {
        server.createContext("/internal/v1/generations/balancing", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 403, "{}");
        });
        server.start();

        AiServiceCallException exception = assertThrows(
            AiServiceCallException.class,
            () -> client().explain(request())
        );

        assertEquals("AI_HTTP_403", exception.getCode());
        assertEquals(1, calls.get());
    }

    private FastApiSetupPrecheckClient client() {
        return new FastApiSetupPrecheckClient(
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build()
        );
    }

    private void capture(HttpExchange exchange) throws IOException {
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastRunId.set(exchange.getRequestHeaders().getFirst("X-AI-Run-Id"));
        lastCorrelationId.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
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

    private static SetupPrecheckContract.Request request() {
        return new SetupPrecheckContract.Request(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "1.2",
            "setup-precheck-v1",
            1,
            NOW.plusSeconds(10),
            "ko-KR",
            5,
            SetupPrecheckContract.AnalysisMode.SETUP_PRECHECK,
            SetupPrecheckContract.TriggerType.TEACHER_REQUEST,
            new SetupPrecheckContract.Payload(
                new SetupPrecheckContract.Context(
                    SetupPrecheckContract.ContextType.CLASSROOM_SETUP,
                    SetupPrecheckContract.CourseScale.ONE_MONTH,
                    24,
                    30,
                    List.of(),
                    null,
                    null,
                    null
                ),
                List.of(),
                List.of(new SetupPrecheckContract.Check(
                    "required-values",
                    SetupPrecheckContract.Severity.PASS,
                    "REQUIRED_VALUES_PRESENT",
                    List.of(),
                    "필수 설정값이 준비되었습니다."
                ))
            )
        );
    }

    private static String responseJson(SetupPrecheckContract.Request request) {
        return """
            {
              "ai_run_id": "%s",
              "schema_version": "1.2",
              "prompt_version": "setup-precheck-v1",
              "provider": "FAKE",
              "model_id": "fake-model",
              "safety_status": "PASSED",
              "validation_codes": [],
              "usage": {"input_tokens": 10, "output_tokens": 5},
              "output": {
                "review_status": "REVIEW_REQUIRED",
                "context_type": "CLASSROOM_SETUP",
                "overall_severity": "PASS",
                "summary": "설정을 확인했습니다.",
                "candidate_explanations": [],
                "check_explanations": [{
                  "check_id": "required-values",
                  "severity": "PASS",
                  "reason_code": "REQUIRED_VALUES_PRESENT",
                  "subject_action_ids": [],
                  "explanation": "필수 설정값이 준비되었습니다.",
                  "correction_guidance": null
                }]
              }
            }
            """.formatted(request.aiRunId());
    }
}
