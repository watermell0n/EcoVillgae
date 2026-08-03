from __future__ import annotations

import json
from copy import deepcopy
from datetime import datetime
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from ecovillage_ai.config import Settings
from ecovillage_ai.main import create_app
from ecovillage_ai.models import Usage
from ecovillage_ai.provider import FakeGenerationProvider
from ecovillage_ai.runtime import GenerationCommand, ProviderResult
from tests.factories import headers, teacher_chat_request

FIXTURE_PATH = Path(__file__).parent / "fixtures" / "teacher_chat_evaluation_v1.json"


class CountingFakeProvider(FakeGenerationProvider):
    def __init__(self) -> None:
        super().__init__()
        self.calls = 0

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        self.calls += 1
        return await super().generate(command)


class LimitedTeacherChatProvider(FakeGenerationProvider):
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        trusted_input = json.loads(command.trusted_input_json)
        payload = trusted_input["payload"]
        return ProviderResult(
            content={
                "response_mode": "LIMITED",
                "answer": "제공된 학급 집계 사실만으로는 이 질문에 답할 수 없습니다.",
                "evidence_fact_ids": [],
                "causes": [],
                "recommendations": [],
                "limitation_codes": ["INSUFFICIENT_EVIDENCE"],
                "period": payload["period"],
                "data_as_of": payload["period"]["data_as_of"],
            },
            usage=Usage(input_tokens=10, output_tokens=10),
        )


class FabricatedNestedEvidenceProvider(FakeGenerationProvider):
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        result = await super().generate(command)
        output = json.loads(result.content)
        output["evidence_fact_ids"].append("fabricated-fact")
        output["recommendations"][0]["evidence_fact_ids"] = ["fabricated-fact"]
        return ProviderResult(content=output, usage=result.usage)


def _client(settings: Settings, provider: object) -> TestClient:
    return TestClient(create_app(settings=settings, provider=provider))


def _evaluation_cases() -> list[dict[str, object]]:
    catalog = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))
    cases = [
        {**deepcopy(case), "grade": grade}
        for grade in catalog["grades"]
        for case in catalog["base_cases"]
    ]
    assert catalog["evaluation_set_version"] == "teacher-chat-evaluation-v1"
    assert catalog["prompt_version"] == "teacher-chat-v1"
    assert len(catalog["base_cases"]) == 20
    assert len(cases) == catalog["expected_case_count"] == 60
    return cases


@pytest.mark.parametrize(
    "case",
    _evaluation_cases(),
    ids=lambda case: f"grade-{case['grade']}-{case['id']}",
)
def test_teacher_chat_evaluation_set(
    settings: Settings, case: dict[str, object]
) -> None:
    body = teacher_chat_request()
    body["grade"] = case["grade"]
    body["payload"]["question"] = case["question"]
    if "facts" in case:
        body["payload"]["facts"] = deepcopy(case["facts"])
    if "recent_policy_changes" in case:
        body["payload"]["recent_policy_changes"] = deepcopy(
            case["recent_policy_changes"]
        )

    kind = case["kind"]
    if kind in {"ANSWER", "GUARD"}:
        provider: object = CountingFakeProvider()
    elif kind == "LIMITED_PROVIDER":
        provider = LimitedTeacherChatProvider()
    else:
        provider = FabricatedNestedEvidenceProvider()

    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=body,
        )

    if kind == "INVALID_PROVIDER":
        assert response.status_code == 422
        assert response.json()["error"]["validation_codes"] == [case["validation_code"]]
        return

    assert response.status_code == 200, response.text
    output = response.json()["output"]
    for field in ("from", "to", "data_as_of"):
        actual = datetime.fromisoformat(output["period"][field].replace("Z", "+00:00"))
        expected = datetime.fromisoformat(body["payload"]["period"][field])
        assert actual == expected
    assert datetime.fromisoformat(output["data_as_of"].replace("Z", "+00:00")) == (
        datetime.fromisoformat(body["payload"]["period"]["data_as_of"])
    )

    if kind == "ANSWER":
        assert output["response_mode"] == "ANSWER"
        assert output["limitation_codes"] == []
        input_ids = {fact["fact_id"] for fact in body["payload"]["facts"]}
        input_ids.update(
            change["fact_id"]
            for change in body["payload"]["recent_policy_changes"]
        )
        assert set(output["evidence_fact_ids"]) <= input_ids
        assert output["causes"]
        assert output["recommendations"]
        for item in (*output["causes"], *output["recommendations"]):
            assert set(item["evidence_fact_ids"]) <= input_ids
        assert provider.calls == 1
    elif kind == "GUARD":
        assert output["response_mode"] == "LIMITED"
        assert case["limitation_code"] in output["limitation_codes"]
        assert output["evidence_fact_ids"] == []
        assert output["causes"] == []
        assert output["recommendations"] == []
        assert response.json()["provider"] == "RULES"
        assert response.json()["model_id"] == "teacher-chat-guard-v1"
        assert response.json()["usage"] == {"input_tokens": 0, "output_tokens": 0}
        assert provider.calls == 0
    else:
        assert output["response_mode"] == "LIMITED"
        assert output["limitation_codes"] == ["INSUFFICIENT_EVIDENCE"]


@pytest.mark.parametrize(
    ("field", "value", "expected_code"),
    [
        ("question", "담당자 이메일 teacher@example.com을 확인해 줘", "SENSITIVE_INPUT_DETECTED"),
        ("fact", "010-1234-5678", "SENSITIVE_INPUT_DETECTED"),
        ("policy", "access_token 값을 교체했습니다", "SECRET_INPUT_DETECTED"),
    ],
)
def test_sensitive_teacher_chat_input_is_rejected_before_provider_call(
    settings: Settings, field: str, value: str, expected_code: str
) -> None:
    body = teacher_chat_request()
    if field == "question":
        body["payload"]["question"] = value
    elif field == "fact":
        body["payload"]["facts"][0]["value"] = value
        body["payload"]["facts"][0]["unit"] = "TEXT"
    else:
        body["payload"]["recent_policy_changes"] = [
            {
                "fact_id": "policy-secret",
                "changed_at": "2026-07-27T09:00:00+09:00",
                "summary": value,
            }
        ]
    provider = CountingFakeProvider()

    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=body,
        )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"
    assert response.json()["error"]["validation_codes"] == [expected_code]
    assert value not in response.text
    assert provider.calls == 0


def test_opaque_non_student_fact_uuid_can_be_returned_as_evidence(
    settings: Settings,
) -> None:
    body = teacher_chat_request()
    fact_id = "6c199e31-f86d-4d98-a88f-86547423161b"
    body["payload"]["facts"][0]["fact_id"] = fact_id

    with _client(settings, CountingFakeProvider()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=body,
        )

    assert response.status_code == 200, response.text
    assert response.json()["output"]["evidence_fact_ids"] == [fact_id]


@pytest.mark.parametrize("invalid_kind", ["duplicate_fact_id", "future_policy"])
def test_invalid_policy_history_context_is_rejected_before_provider_call(
    settings: Settings, invalid_kind: str
) -> None:
    body = teacher_chat_request()
    body["payload"]["recent_policy_changes"] = [
        {
            "fact_id": (
                body["payload"]["facts"][0]["fact_id"]
                if invalid_kind == "duplicate_fact_id"
                else "future-policy"
            ),
            "changed_at": (
                "2026-07-20T09:00:00+09:00"
                if invalid_kind == "duplicate_fact_id"
                else "2100-01-01T00:00:00+00:00"
            ),
            "summary": "정책 변경 이력입니다.",
        }
    ]
    provider = CountingFakeProvider()

    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=body,
        )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"
    assert provider.calls == 0
