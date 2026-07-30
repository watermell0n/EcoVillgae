from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

from fastapi.testclient import TestClient

from ecovillage_ai.config import Settings
from ecovillage_ai.main import create_app
from ecovillage_ai.models import Usage
from ecovillage_ai.provider import FakeGenerationProvider
from ecovillage_ai.runtime import GenerationCommand, ProviderResult
from ecovillage_ai.telemetry import InMemoryRunEventSink, RunEventSink, SafeRunEvent
from ecovillage_ai.validation import OutputValidationPipeline, ValidationStage
from tests.factories import balancing_request, headers, teacher_chat_request


@dataclass
class CapturingProvider:
    commands: list[GenerationCommand] = field(default_factory=list)

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        self.commands.append(command)
        return await FakeGenerationProvider().generate(command)


class MalformedJsonProvider:
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        return ProviderResult(
            content="{not-json",
            usage=Usage(input_tokens=10, output_tokens=2),
        )


class JsonArrayProvider:
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        return ProviderResult(
            content="[]",
            usage=Usage(input_tokens=10, output_tokens=2),
        )


class UnsupportedContentProvider:
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        return ProviderResult(  # type: ignore[arg-type]
            content=["not", "supported"],
            usage=Usage(input_tokens=10, output_tokens=2),
        )


class UnexpectedFailureProvider:
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        raise RuntimeError("raw-secret-provider-message")


class FailingRunEventSink:
    def record(self, event: SafeRunEvent) -> None:
        raise RuntimeError("telemetry backend unavailable")


class OrderedViolationProvider:
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        result = await FakeGenerationProvider().generate(command)
        output = json.loads(result.content)
        output["evidence_metric_codes"] = ["FABRICATED_METRIC"]
        output["summary"] = "teacher@example.com"
        return ProviderResult(content=output, usage=result.usage)


class OutOfRangeProvider:
    async def generate(self, command: GenerationCommand) -> ProviderResult:
        result = await FakeGenerationProvider().generate(command)
        output = json.loads(result.content)
        output["proposals"][0]["proposed_value"] = 999999
        return ProviderResult(content=output, usage=result.usage)


class TeacherAnswerProvider:
    answer: str

    def __init__(self, answer: str) -> None:
        self.answer = answer

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        result = await FakeGenerationProvider().generate(command)
        output = json.loads(result.content)
        output["answer"] = self.answer
        return ProviderResult(content=output, usage=result.usage)


def _client(
    settings: Settings,
    provider: Any,
    event_sink: RunEventSink | None = None,
) -> TestClient:
    return TestClient(
        create_app(settings=settings, provider=provider, event_sink=event_sink)
    )


def test_provider_receives_only_separated_generation_command(settings: Settings) -> None:
    marker = "이번 주 소비 흐름과 원인을 자세히 설명해 주세요."
    body = teacher_chat_request()
    body["payload"]["question"] = marker
    provider = CapturingProvider()
    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat", headers=headers(), json=body
        )

    assert response.status_code == 200, response.text
    assert len(provider.commands) == 1
    command = provider.commands[0]
    assert marker in command.trusted_input_json
    assert marker not in command.system_instruction
    assert command.output_schema["title"] == "TeacherChatOutput"


def test_malformed_provider_json_is_safe_schema_error(settings: Settings) -> None:
    with _client(settings, MalformedJsonProvider()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "AI_OUTPUT_SCHEMA_INVALID"
    assert response.json()["error"]["validation_codes"] == ["OUTPUT_JSON_INVALID"]
    assert "not-json" not in response.text


def test_non_object_provider_json_is_rejected(settings: Settings) -> None:
    with _client(settings, JsonArrayProvider()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["validation_codes"] == [
        "OUTPUT_JSON_OBJECT_REQUIRED"
    ]


def test_unsupported_provider_content_type_is_rejected(settings: Settings) -> None:
    with _client(settings, UnsupportedContentProvider()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["validation_codes"] == [
        "OUTPUT_CONTENT_TYPE_INVALID"
    ]


def test_validation_pipeline_order_is_explicit_and_stable() -> None:
    assert OutputValidationPipeline.stages == (
        ValidationStage.SCHEMA,
        ValidationStage.CONTEXT,
        ValidationStage.EVIDENCE,
        ValidationStage.RANGE,
        ValidationStage.GRADE_AND_EFFECT,
        ValidationStage.CONTENT_SAFETY,
        ValidationStage.APPROVAL_BOUNDARY,
    )


def test_unexpected_provider_error_is_mapped_without_raw_detail(settings: Settings) -> None:
    with _client(settings, UnexpectedFailureProvider()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 503
    assert response.json()["error"]["validation_codes"] == [
        "PROVIDER_UNEXPECTED_ERROR"
    ]
    assert "raw-secret-provider-message" not in response.text


def test_event_sink_failure_does_not_change_success_response(settings: Settings) -> None:
    with _client(settings, FakeGenerationProvider(), FailingRunEventSink()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 200, response.text


def test_validation_stops_at_evidence_before_content_safety(settings: Settings) -> None:
    with _client(settings, OrderedViolationProvider()) as client:
        response = client.post(
            "/internal/v1/generations/balancing",
            headers=headers(),
            json=balancing_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["validation_codes"] == ["FABRICATED_EVIDENCE"]


def test_out_of_range_value_is_rejected(settings: Settings) -> None:
    with _client(settings, OutOfRangeProvider()) as client:
        response = client.post(
            "/internal/v1/generations/balancing",
            headers=headers(),
            json=balancing_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["validation_codes"] == [
        "PROPOSED_VALUE_OUT_OF_RANGE"
    ]


def test_financial_advice_and_approval_bypass_are_separate_stages(
    settings: Settings,
) -> None:
    cases = [
        ("이 상품에 투자하세요", "FINANCIAL_ADVICE_DETECTED"),
        ("정책이 자동 적용되었습니다", "APPROVAL_BOUNDARY_VIOLATION"),
    ]
    for answer, expected_code in cases:
        with _client(settings, TeacherAnswerProvider(answer)) as client:
            response = client.post(
                "/internal/v1/generations/teacher-chat",
                headers=headers(),
                json=teacher_chat_request(),
            )
        assert response.status_code == 422
        assert response.json()["error"]["validation_codes"] == [expected_code]


def test_success_and_failure_events_contain_only_safe_metadata(
    settings: Settings,
) -> None:
    marker = "로그에 남으면 안 되는 질문 원문"
    success_body = teacher_chat_request()
    success_body["payload"]["question"] = marker
    sink = InMemoryRunEventSink()
    with _client(settings, FakeGenerationProvider(), sink) as client:
        success = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=success_body,
        )
    with _client(settings, MalformedJsonProvider(), sink) as client:
        failure = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert success.status_code == 200
    assert failure.status_code == 422
    assert [event.outcome for event in sink.events] == ["SUCCEEDED", "FAILED"]
    assert sink.events[1].validation_stage == "SCHEMA"
    serialized_events = json.dumps(
        [event.as_dict() for event in sink.events], ensure_ascii=False
    )
    assert marker not in serialized_events
    assert "system_instruction" not in serialized_events
    assert "trusted_input" not in serialized_events
    assert "payload" not in serialized_events


def test_prompt_version_mismatch_does_not_call_provider_and_is_logged(
    settings: Settings,
) -> None:
    body = teacher_chat_request()
    body["prompt_version"] = "teacher-chat-v2"
    provider = CapturingProvider()
    sink = InMemoryRunEventSink()
    with _client(settings, provider, sink) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat", headers=headers(), json=body
        )

    assert response.status_code == 409
    assert response.json()["error"]["validation_codes"] == [
        "PROMPT_VERSION_MISMATCH"
    ]
    assert provider.commands == []
    assert sink.events[0].validation_stage == "CONTEXT"
