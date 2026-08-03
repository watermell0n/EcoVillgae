from __future__ import annotations

from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import uuid4

import pytest
import yaml
from fastapi.testclient import TestClient

from tests.factories import (
    AI_RUN_ID,
    CORRELATION_ID,
    auto_monitor_request,
    balancing_request,
    economic_event_request,
    headers,
    investment_news_request,
    quiz_draft_request,
    setup_precheck_request,
    teacher_chat_request,
)

CASES = [
    ("/internal/v1/generations/balancing", balancing_request, {"summary", "proposals"}),
    (
        "/internal/v1/generations/teacher-chat",
        teacher_chat_request,
        {
            "response_mode",
            "answer",
            "evidence_fact_ids",
            "causes",
            "recommendations",
            "limitation_codes",
            "period",
            "data_as_of",
        },
    ),
    (
        "/internal/v1/generations/economic-event",
        economic_event_request,
        {"review_status", "narrative"},
    ),
    (
        "/internal/v1/generations/quiz-draft",
        quiz_draft_request,
        {"review_status", "questions"},
    ),
]


@pytest.mark.parametrize(("path", "factory", "output_fields"), CASES)
def test_generation_endpoints_follow_success_envelope(
    client: TestClient, path: str, factory: object, output_fields: set[str]
) -> None:
    response = client.post(path, headers=headers(), json=factory())

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["ai_run_id"] == str(AI_RUN_ID)
    assert body["schema_version"] == "1.2"
    assert body["provider"] == "FAKE"
    assert body["safety_status"] == "PASSED"
    assert output_fields <= body["output"].keys()
    assert response.headers["X-Correlation-Id"] == str(CORRELATION_ID)
    assert response.headers["X-AI-Run-Id"] == str(AI_RUN_ID)


def test_health_check(client: TestClient) -> None:
    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "이코빌리지 AI 서비스",
        "version": "0.1.0",
        "provider": "FAKE",
        "schema_version": "1.2",
        "prompt_versions": [
            "setup-precheck-v1",
            "balancing-v1",
            "teacher-chat-v1",
            "economic-event-v1",
            "quiz-draft-v1",
        ],
        "provider_routes": {
            "balancing": {"provider": "FAKE", "model_id": "deterministic-v1"},
            "teacher-chat": {"provider": "FAKE", "model_id": "deterministic-v1"},
            "economic-event": {"provider": "FAKE", "model_id": "deterministic-v1"},
            "quiz-draft": {"provider": "FAKE", "model_id": "deterministic-v1"},
        },
    }


def test_unknown_input_field_is_safe_bad_request(client: TestClient) -> None:
    body = balancing_request()
    body["classroom_id"] = str(uuid4())

    response = client.post("/internal/v1/generations/balancing", headers=headers(), json=body)

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"
    assert "classroom_id" not in response.text


def test_header_body_context_mismatch_is_conflict(client: TestClient) -> None:
    request_headers = headers()
    request_headers["X-AI-Run-Id"] = str(uuid4())

    response = client.post(
        "/internal/v1/generations/balancing",
        headers=request_headers,
        json=balancing_request(),
    )

    assert response.status_code == 409
    assert response.json()["error"]["code"] == "AI_RUN_CONTEXT_MISMATCH"


def test_invalid_service_token_is_forbidden(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/generations/teacher-chat",
        headers=headers("invalid-token"),
        json=teacher_chat_request(),
    )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "AI_SERVICE_SCOPE_VIOLATION"
    assert "invalid-token" not in response.text


def test_expired_deadline_does_not_generate(client: TestClient) -> None:
    body = teacher_chat_request()
    body["deadline_at"] = (datetime.now(UTC) - timedelta(seconds=1)).isoformat()

    response = client.post("/internal/v1/generations/teacher-chat", headers=headers(), json=body)

    assert response.status_code == 504
    assert response.json()["error"]["code"] == "AI_DEADLINE_EXCEEDED"


def test_quiz_ox_output_has_exact_ox_choices(client: TestClient) -> None:
    body = quiz_draft_request()
    body["payload"]["question_type"] = "OX"
    body["payload"]["count"] = 1

    response = client.post("/internal/v1/generations/quiz-draft", headers=headers(), json=body)

    assert response.status_code == 200
    question = response.json()["output"]["questions"][0]
    assert {choice["choice_key"] for choice in question["choices"]} == {"O", "X"}
    assert question["correct_choice_key"] in {"O", "X"}
    assert question["difficulty"] == "STANDARD"
    assert question["concept_tags"] == ["필요와 욕구"]


@pytest.mark.parametrize(
    "context_type",
    ["CLASSROOM_SETUP", "MODULE_ACTIVATION", "PRODUCT_DRAFT", "QUIZ_SESSION_DRAFT"],
)
def test_setup_precheck_contexts_return_exact_candidates(
    client: TestClient, context_type: str
) -> None:
    body = setup_precheck_request(context_type)

    response = client.post("/internal/v1/generations/balancing", headers=headers(), json=body)

    assert response.status_code == 200, response.text
    output = response.json()["output"]
    assert output["context_type"] == context_type
    assert output["review_status"] == "REVIEW_REQUIRED"
    assert [item["action_id"] for item in output["candidate_explanations"]] == [
        item["action_id"] for item in body["payload"]["candidates"]
    ]


def test_auto_monitor_requires_previous_period_and_generates_warn(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/generations/balancing", headers=headers(), json=auto_monitor_request()
    )

    assert response.status_code == 200, response.text
    assert response.json()["output"]["proposals"]


def test_auto_monitor_normal_does_not_call_model(client: TestClient) -> None:
    body = auto_monitor_request()
    body["payload"]["analysis_severity"] = "NORMAL"

    response = client.post("/internal/v1/generations/balancing", headers=headers(), json=body)

    assert response.status_code == 422
    assert response.json()["error"]["validation_codes"] == [
        "AUTO_MONITOR_NORMAL_MODEL_CALL_FORBIDDEN"
    ]


def test_setup_context_rejects_unrelated_candidate(client: TestClient) -> None:
    body = setup_precheck_request("PRODUCT_DRAFT")
    body["payload"]["candidates"][0]["target_type"] = "ROLE_SALARY"

    response = client.post("/internal/v1/generations/balancing", headers=headers(), json=body)

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"


def test_monitor_event_requires_source_reference(client: TestClient) -> None:
    body = economic_event_request()
    body["payload"]["source_type"] = "MONITOR_RECOMMENDATION"

    response = client.post(
        "/internal/v1/generations/economic-event", headers=headers(), json=body
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"


def test_investment_news_preserves_scenario_asset_and_step(client: TestClient) -> None:
    body = investment_news_request()

    response = client.post(
        "/internal/v1/generations/economic-event", headers=headers(), json=body
    )

    assert response.status_code == 200, response.text
    binding = response.json()["output"]["investment_news_binding"]
    context = body["payload"]["investment_news_context"]
    assert binding["scenario_version_id"] == context["scenario_version_id"]
    assert binding["asset_id"] == context["asset_id"]
    assert binding["price_step_id"] == context["price_step_id"]
    assert binding["current_step_index"] == context["current_step_index"]


def test_investment_news_rejects_future_price_fact(client: TestClient) -> None:
    body = investment_news_request()
    body["payload"]["investment_news_context"]["public_price_facts"].append(
        {
            "step_index": 3,
            "price": 120,
            "change_rate_bps": 1429,
            "revealed_at": "2026-07-15T00:00:00+00:00",
        }
    )

    response = client.post(
        "/internal/v1/generations/economic-event", headers=headers(), json=body
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"


def test_event_template_is_restricted_by_grade(client: TestClient) -> None:
    body = economic_event_request()
    body["grade"] = 4

    response = client.post("/internal/v1/generations/economic-event", headers=headers(), json=body)

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "AI_OUTPUT_SAFETY_REJECTED"
    assert response.json()["error"]["validation_codes"] == ["GRADE_POLICY_VIOLATION"]


def test_openapi_exposes_exact_internal_generation_paths(client: TestClient) -> None:
    schema = client.get("/openapi.json").json()
    contract_path = Path(__file__).parents[2] / "contracts" / "ai-internal-openapi.yaml"
    contract = yaml.safe_load(contract_path.read_text(encoding="utf-8"))
    generation_paths = {
        path for path in schema["paths"] if path.startswith("/internal/v1/generations/")
    }

    assert generation_paths == set(contract["paths"]) == {path for path, _, _ in CASES}
    for path in generation_paths:
        actual_operation = schema["paths"][path]["post"]
        contract_operation = contract["paths"][path]["post"]
        assert actual_operation["operationId"] == contract_operation["operationId"]
        assert actual_operation["x-required-scope"] == contract_operation["x-required-scope"]
    security_scheme = schema["components"]["securitySchemes"]["internalServiceBearer"]
    assert security_scheme["scheme"] == "bearer"


def test_openapi_and_pydantic_expose_v1_2_feature_dimensions(client: TestClient) -> None:
    generated = client.get("/openapi.json").json()
    contract_path = Path(__file__).parents[2] / "contracts" / "ai-internal-openapi.yaml"
    contract = yaml.safe_load(contract_path.read_text(encoding="utf-8"))
    generated_schemas = generated["components"]["schemas"]
    contract_schemas = contract["components"]["schemas"]

    assert contract["info"]["version"] == "1.2.0"
    assert generated_schemas["AnalysisMode"]["enum"] == [
        "SETUP_PRECHECK",
        "ON_DEMAND",
        "AUTO_MONITOR",
    ]
    assert contract_schemas["SetupContext"]["properties"]["context_type"]["enum"] == [
        "CLASSROOM_SETUP",
        "MODULE_ACTIVATION",
        "PRODUCT_DRAFT",
        "QUIZ_SESSION_DRAFT",
    ]
    assert {
        "recommended_min_value",
        "recommended_max_value",
        "allowed_min_value",
        "allowed_max_value",
    } <= set(contract_schemas["SetupCandidate"]["required"])
    assert contract_schemas["EconomicEventPayload"]["properties"]["source_type"][
        "enum"
    ] == ["TEACHER_REQUEST", "MONITOR_RECOMMENDATION"]
    assert {
        "course_scale",
        "source_type",
        "source_proposal_id",
        "source_evidence_codes",
        "template_type",
        "interest_rate_scope",
        "effect_summary",
        "prohibited_effects",
        "teacher_context",
        "investment_news_context",
    } == set(contract_schemas["EconomicEventPayload"]["required"])
    assert {
        "source_type",
        "source_proposal_id",
        "source_evidence_codes",
        "interest_rate_scope",
    } <= set(contract_schemas["EconomicEventOutput"]["required"])
    assert contract_schemas["EconomicEventOutput"]["properties"]["choices"][
        "minItems"
    ] == 2
    assert generated_schemas["QuizDifficulty"]["enum"] == [
        "INTRODUCTORY",
        "STANDARD",
        "ADVANCED",
    ]
    assert {"difficulty", "concepts"} <= set(
        contract_schemas["QuizDraftPayload"]["required"]
    )
    assert {
        "scenario_version_id",
        "asset_id",
        "price_step_id",
        "asset_name",
        "asset_symbol",
        "current_step_index",
        "public_price_facts",
    } <= set(contract_schemas["InvestmentNewsContext"]["required"])
    assert {
        "scenario_version_id",
        "asset_id",
        "price_step_id",
        "asset_name",
        "asset_symbol",
        "current_step_index",
    } == set(contract_schemas["InvestmentNewsBinding"]["required"])
    assert generated_schemas["TeacherChatResponseMode"]["enum"] == [
        "ANSWER",
        "LIMITED",
    ]
    assert {
        "response_mode",
        "answer",
        "evidence_fact_ids",
        "causes",
        "recommendations",
        "limitation_codes",
        "period",
        "data_as_of",
    } == set(contract_schemas["TeacherChatOutput"]["required"])
    assert "evidence_fact_ids" in contract_schemas["Recommendation"]["required"]
