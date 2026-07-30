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
    balancing_request,
    economic_event_request,
    headers,
    quiz_draft_request,
    teacher_chat_request,
)

CASES = [
    ("/internal/v1/generations/balancing", balancing_request, {"summary", "proposals"}),
    ("/internal/v1/generations/teacher-chat", teacher_chat_request, {"answer", "period"}),
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
    assert body["schema_version"] == "1.0"
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
        "service": "BIBIM AI Service",
        "version": "0.1.0",
        "provider": "FAKE",
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
