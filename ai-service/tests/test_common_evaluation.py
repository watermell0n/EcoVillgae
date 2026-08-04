from __future__ import annotations

import json
from collections.abc import Callable
from copy import deepcopy
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from ecovillage_ai.config import Settings
from ecovillage_ai.errors import ProviderUnavailableError
from ecovillage_ai.main import create_app
from ecovillage_ai.provider import FakeGenerationProvider
from ecovillage_ai.runtime import GenerationCommand, ProviderResult
from tests.factories import (
    balancing_request,
    economic_event_request,
    headers,
    investment_news_request,
    quiz_draft_request,
    setup_precheck_request,
    teacher_chat_request,
)

FIXTURE_PATH = Path(__file__).parent / "fixtures" / "common_evaluation_v1.json"
CATALOG = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))
RequestFactory = Callable[[], dict[str, Any]]
FACTORIES: dict[str, RequestFactory] = {
    "setup_precheck": setup_precheck_request,
    "balancing": balancing_request,
    "teacher_chat": teacher_chat_request,
    "economic_event": economic_event_request,
    "investment_news": investment_news_request,
    "quiz_draft": quiz_draft_request,
}


class CommonEvaluationProvider(FakeGenerationProvider):
    def __init__(self, mutation: str | None = None, *, unavailable: bool = False) -> None:
        super().__init__()
        self.mutation = mutation
        self.unavailable = unavailable
        self.calls = 0

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        self.calls += 1
        if self.unavailable:
            raise ProviderUnavailableError("common evaluation provider failure")
        result = await super().generate(command)
        if self.mutation is None:
            return result
        output = json.loads(result.content)
        field = _natural_language_target(command, output)
        messages = {
            "SENSITIVE": "교사 이메일은 teacher@example.com 입니다.",
            "SECRET": "access_token 값을 출력합니다.",
            "APPROVAL": "교사 승인 없이 자동 적용합니다.",
        }
        field[0][field[1]] = messages[self.mutation]
        return ProviderResult(content=output, usage=result.usage)


def _natural_language_target(
    command: GenerationCommand, output: dict[str, Any]
) -> tuple[dict[str, Any], str]:
    if command.kind.value == "balancing":
        return output, "summary"
    if command.kind.value == "teacher-chat":
        return output, "answer"
    if command.kind.value == "economic-event":
        return output, "narrative"
    return output["questions"][0], "question"


def _cases() -> list[dict[str, Any]]:
    cases = [
        {"feature": deepcopy(feature), "scenario": deepcopy(scenario)}
        for feature in CATALOG["features"]
        for scenario in CATALOG["scenarios"]
    ]
    assert CATALOG["evaluation_set_version"] == "common-evaluation-v1"
    assert CATALOG["schema_version"] == "1.2"
    assert len(FACTORIES) == len(CATALOG["features"]) == 6
    assert len(CATALOG["scenarios"]) == 10
    assert len(cases) == CATALOG["expected_case_count"] == 60
    return cases


@pytest.mark.parametrize(
    "case",
    _cases(),
    ids=lambda case: f"{case['feature']['id']}-{case['scenario']['id']}",
)
def test_common_evaluation_set(settings: Settings, case: dict[str, Any]) -> None:
    feature = case["feature"]
    scenario = case["scenario"]
    body = FACTORIES[feature["factory"]]()
    request_headers = headers()
    provider = CommonEvaluationProvider(
        scenario.get("mutation"),
        unavailable=scenario["kind"] == "PROVIDER",
    )

    if scenario["id"] == "additional-field":
        body["undeclared_field"] = "not-allowed"
    elif scenario["id"] == "missing-schema-version":
        body.pop("schema_version")
    elif scenario["id"] == "missing-auth":
        request_headers.pop("Authorization")
    elif scenario["id"] == "context-mismatch":
        request_headers["X-AI-Run-Id"] = str(uuid4())
    elif scenario["id"] == "expired-deadline":
        body["deadline_at"] = (datetime.now(UTC) - timedelta(seconds=1)).isoformat()

    with TestClient(create_app(settings=settings, provider=provider)) as client:
        response = client.post(
            feature["endpoint"], headers=request_headers, json=body
        )

    assert provider.calls == scenario["expected_provider_calls"]
    assert response.status_code == scenario["expected_status"], response.text
    if scenario["kind"] == "SUCCESS":
        assert response.json()["safety_status"] == "PASSED"
        return
    error = response.json()["error"]
    assert error["code"] == scenario["expected_error"]
    if "validation_code" in scenario:
        assert scenario["validation_code"] in error["validation_codes"]
