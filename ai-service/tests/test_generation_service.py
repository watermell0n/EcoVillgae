from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from fastapi.testclient import TestClient

from bibim_ai.config import Settings
from bibim_ai.errors import ProviderRateLimitedError, ProviderUnavailableError
from bibim_ai.main import create_app
from bibim_ai.models import Usage
from bibim_ai.provider import FakeGenerationProvider, GenerationKind, ProviderResult
from tests.factories import balancing_request, headers, teacher_chat_request


@dataclass
class RaisingProvider:
    error: Exception
    calls: int = 0

    async def generate(self, kind: GenerationKind, request: Any) -> ProviderResult:
        self.calls += 1
        raise self.error


class FabricatedEvidenceProvider:
    async def generate(self, kind: GenerationKind, request: Any) -> ProviderResult:
        result = await FakeGenerationProvider().generate(kind, request)
        result.output["evidence_metric_codes"] = ["FABRICATED_METRIC"]
        return result


class UnexpectedOutputFieldProvider:
    async def generate(self, kind: GenerationKind, request: Any) -> ProviderResult:
        result = await FakeGenerationProvider().generate(kind, request)
        result.output["direct_apply_command"] = "apply"
        return result


class SensitiveOutputProvider:
    async def generate(self, kind: GenerationKind, request: Any) -> ProviderResult:
        result = await FakeGenerationProvider().generate(kind, request)
        result.output["answer"] = "연락처는 teacher@example.com 입니다."
        return result


class SlowProvider:
    async def generate(self, kind: GenerationKind, request: Any) -> ProviderResult:
        await asyncio.sleep(0.2)
        return ProviderResult(output={}, usage=Usage(input_tokens=0, output_tokens=0))


def _client(settings: Settings, provider: Any) -> TestClient:
    return TestClient(create_app(settings=settings, provider=provider))


def test_fake_provider_is_deterministic(client: TestClient) -> None:
    request_body = balancing_request()

    first = client.post("/internal/v1/generations/balancing", headers=headers(), json=request_body)
    second = client.post("/internal/v1/generations/balancing", headers=headers(), json=request_body)

    assert first.status_code == second.status_code == 200
    assert first.json() == second.json()


def test_fabricated_evidence_is_rejected(settings: Settings) -> None:
    with _client(settings, FabricatedEvidenceProvider()) as client:
        response = client.post(
            "/internal/v1/generations/balancing",
            headers=headers(),
            json=balancing_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "AI_OUTPUT_SCHEMA_INVALID"
    assert "FABRICATED_EVIDENCE" in response.json()["error"]["validation_codes"]


def test_unknown_provider_output_field_is_rejected(settings: Settings) -> None:
    with _client(settings, UnexpectedOutputFieldProvider()) as client:
        response = client.post(
            "/internal/v1/generations/balancing",
            headers=headers(),
            json=balancing_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "AI_OUTPUT_SCHEMA_INVALID"
    assert "direct_apply_command" not in response.text


def test_sensitive_provider_output_is_rejected(settings: Settings) -> None:
    with _client(settings, SensitiveOutputProvider()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "AI_OUTPUT_SAFETY_REJECTED"
    assert response.json()["error"]["validation_codes"] == ["SENSITIVE_DATA_DETECTED"]
    assert "teacher@example.com" not in response.text


def test_rate_limit_is_mapped_without_hidden_retry(settings: Settings) -> None:
    provider = RaisingProvider(ProviderRateLimitedError("raw-provider-detail"))
    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert provider.calls == 1
    assert response.status_code == 429
    assert response.json()["error"] == {
        "code": "AI_PROVIDER_RATE_LIMITED",
        "message": "AI 공급자 호출이 일시적으로 제한되었습니다.",
        "retryable": True,
        "validation_codes": ["PROVIDER_RATE_LIMITED"],
    }
    assert "raw-provider-detail" not in response.text


def test_second_attempt_provider_failure_is_not_retryable(settings: Settings) -> None:
    provider = RaisingProvider(ProviderUnavailableError("raw-provider-detail"))
    body = teacher_chat_request()
    body["attempt"] = 2
    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat", headers=headers(), json=body
        )

    assert provider.calls == 1
    assert response.status_code == 503
    assert response.json()["error"]["retryable"] is False


def test_provider_timeout_is_safe_deadline_error(settings: Settings) -> None:
    timeout_settings = Settings(
        environment="test",
        internal_service_token=settings.internal_service_token,
        internal_service_scopes=settings.internal_service_scopes,
        provider_timeout_seconds=0.01,
        minimum_deadline_remaining_seconds=0.001,
    )
    with _client(timeout_settings, SlowProvider()) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 504
    assert response.json()["error"]["code"] == "AI_DEADLINE_EXCEEDED"
    assert response.json()["error"]["retryable"] is False
