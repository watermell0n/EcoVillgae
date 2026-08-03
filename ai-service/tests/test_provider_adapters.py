from __future__ import annotations

import json
from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any

import pytest

from ecovillage_ai.errors import (
    ProviderConfigurationError,
    ProviderRateLimitedError,
    ProviderTimeoutError,
    ProviderUnavailableError,
)
from ecovillage_ai.models import TeacherChatGenerationRequest, TeacherChatOutput, Usage
from ecovillage_ai.prompting import PromptRegistry
from ecovillage_ai.provider import FakeGenerationProvider
from ecovillage_ai.providers.bedrock import BedrockGenerationProvider
from ecovillage_ai.providers.openai import OpenAIGenerationProvider
from ecovillage_ai.providers.routing import RoutedGenerationProvider
from ecovillage_ai.providers.schema import schema_name, strict_output_schema
from ecovillage_ai.runtime import GenerationCommand, GenerationKind, ProviderResult
from tests.factories import teacher_chat_request


def _teacher_command() -> GenerationCommand:
    request = TeacherChatGenerationRequest.model_validate(teacher_chat_request())
    return PromptRegistry().build_command(
        GenerationKind.TEACHER_CHAT,
        request,
        TeacherChatOutput,
    )


@dataclass
class BedrockClientStub:
    content: str
    calls: list[dict[str, Any]] = field(default_factory=list)
    error: Exception | None = None

    def converse(self, **kwargs: Any) -> dict[str, Any]:
        self.calls.append(kwargs)
        if self.error is not None:
            raise self.error
        return {
            "output": {"message": {"content": [{"text": self.content}]}},
            "usage": {"inputTokens": 31, "outputTokens": 17},
        }


@dataclass
class OpenAIResponsesStub:
    content: str
    calls: list[dict[str, Any]] = field(default_factory=list)
    error: Exception | None = None

    async def create(self, **kwargs: Any) -> Any:
        self.calls.append(kwargs)
        if self.error is not None:
            raise self.error
        return SimpleNamespace(
            status="completed",
            output_text=self.content,
            usage=SimpleNamespace(input_tokens=29, output_tokens=13),
        )


async def _valid_content(command: GenerationCommand) -> str:
    result = await FakeGenerationProvider().generate(command)
    assert isinstance(result.content, str)
    return result.content


@pytest.mark.asyncio
async def test_bedrock_uses_converse_strict_schema_without_hidden_retry() -> None:
    command = _teacher_command()
    client = BedrockClientStub(await _valid_content(command))
    provider = BedrockGenerationProvider(
        model_id="bedrock-model",
        region_name="ap-northeast-2",
        timeout_seconds=3,
        max_output_tokens=2048,
        client=client,
    )

    result = await provider.generate(command)

    assert result.usage == Usage(input_tokens=31, output_tokens=17)
    assert len(client.calls) == 1
    request = client.calls[0]
    assert request["modelId"] == "bedrock-model"
    assert request["system"] == [{"text": command.system_instruction}]
    assert request["messages"][0]["content"] == [
        {"text": command.trusted_input_json}
    ]
    schema_config = request["outputConfig"]["textFormat"]["structure"]["jsonSchema"]
    assert json.loads(schema_config["schema"]) == strict_output_schema(
        command.output_schema
    )


@pytest.mark.asyncio
async def test_openai_uses_responses_strict_schema_without_hidden_retry() -> None:
    command = _teacher_command()
    responses = OpenAIResponsesStub(await _valid_content(command))
    provider = OpenAIGenerationProvider(
        model_id="openai-model",
        timeout_seconds=3,
        max_output_tokens=2048,
        client=SimpleNamespace(responses=responses),
    )

    result = await provider.generate(command)

    assert result.usage == Usage(input_tokens=29, output_tokens=13)
    assert len(responses.calls) == 1
    request = responses.calls[0]
    assert request["model"] == "openai-model"
    assert request["instructions"] == command.system_instruction
    assert request["input"] == command.trusted_input_json
    assert request["text"]["format"] == {
        "type": "json_schema",
        "name": schema_name(command.prompt_key),
        "description": "이코빌리지 내부 AI 구조화 생성 결과",
        "strict": True,
        "schema": strict_output_schema(command.output_schema),
    }
    assert request["store"] is False


def test_strict_schema_keeps_optional_fields_nullable_but_required() -> None:
    schema = strict_output_schema(
        {
            "type": "object",
            "properties": {
                "required_value": {"type": "string", "minLength": 1},
                "optional_value": {
                    "anyOf": [{"type": "string"}, {"type": "null"}],
                    "default": None,
                },
            },
            "required": ["required_value"],
        }
    )

    assert schema["required"] == ["required_value", "optional_value"]
    assert schema["additionalProperties"] is False
    assert "minLength" not in schema["properties"]["required_value"]
    assert "default" not in schema["properties"]["optional_value"]


class ProviderFailure(Exception):
    def __init__(self, code: str, status: int) -> None:
        super().__init__("raw-provider-detail")
        self.response = {
            "Error": {"Code": code, "Message": "raw-provider-detail"},
            "ResponseMetadata": {"HTTPStatusCode": status},
        }
        self.status_code = status


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("code", "status", "expected"),
    [
        ("ThrottlingException", 429, ProviderRateLimitedError),
        ("ModelTimeoutException", 408, ProviderTimeoutError),
        ("AccessDeniedException", 403, ProviderConfigurationError),
        ("ServiceUnavailableException", 503, ProviderUnavailableError),
    ],
)
async def test_bedrock_maps_errors_without_raw_detail(
    code: str,
    status: int,
    expected: type[Exception],
) -> None:
    provider = BedrockGenerationProvider(
        model_id="bedrock-model",
        region_name="ap-northeast-2",
        timeout_seconds=3,
        max_output_tokens=2048,
        client=BedrockClientStub("", error=ProviderFailure(code, status)),
    )

    with pytest.raises(expected) as caught:
        await provider.generate(_teacher_command())

    assert "raw-provider-detail" not in str(caught.value)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "expected"),
    [
        (429, ProviderRateLimitedError),
        (408, ProviderTimeoutError),
        (401, ProviderConfigurationError),
        (503, ProviderUnavailableError),
    ],
)
async def test_openai_maps_errors_without_raw_detail(
    status: int,
    expected: type[Exception],
) -> None:
    responses = OpenAIResponsesStub("", error=ProviderFailure("OpenAIError", status))
    provider = OpenAIGenerationProvider(
        model_id="openai-model",
        timeout_seconds=3,
        max_output_tokens=2048,
        client=SimpleNamespace(responses=responses),
    )

    with pytest.raises(expected) as caught:
        await provider.generate(_teacher_command())

    assert "raw-provider-detail" not in str(caught.value)


@dataclass
class RoutedStubProvider:
    provider_name: str
    model_id: str
    calls: int = 0

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        self.calls += 1
        return ProviderResult(content={}, usage=Usage(input_tokens=0, output_tokens=0))

    def provider_name_for(self, kind: GenerationKind) -> str:
        return self.provider_name

    def model_id_for(self, kind: GenerationKind) -> str:
        return self.model_id


@pytest.mark.asyncio
async def test_router_calls_only_the_selected_feature_provider() -> None:
    fake = RoutedStubProvider("FAKE", "fake-model")
    openai = RoutedStubProvider("OPENAI", "openai-model")
    router = RoutedGenerationProvider(
        {
            GenerationKind.BALANCING: fake,
            GenerationKind.TEACHER_CHAT: openai,
            GenerationKind.ECONOMIC_EVENT: fake,
            GenerationKind.QUIZ_DRAFT: fake,
        }
    )

    await router.generate(_teacher_command())

    assert openai.calls == 1
    assert fake.calls == 0
    assert router.provider_name_for(GenerationKind.TEACHER_CHAT) == "OPENAI"
    assert router.model_id_for(GenerationKind.TEACHER_CHAT) == "openai-model"


def test_openai_sdk_client_disables_automatic_retries(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    import openai

    captured: dict[str, Any] = {}

    class AsyncOpenAIStub:
        def __init__(self, **kwargs: Any) -> None:
            captured.update(kwargs)

    monkeypatch.setattr(openai, "AsyncOpenAI", AsyncOpenAIStub)

    OpenAIGenerationProvider(
        model_id="openai-model",
        timeout_seconds=7,
        max_output_tokens=2048,
    )

    assert captured["timeout"] == 7
    assert captured["max_retries"] == 0
    assert "api_key" not in captured


def test_bedrock_sdk_client_allows_only_one_total_attempt(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    import boto3

    captured: dict[str, Any] = {}

    def client_stub(service_name: str, **kwargs: Any) -> object:
        captured["service_name"] = service_name
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(boto3, "client", client_stub)

    BedrockGenerationProvider(
        model_id="bedrock-model",
        region_name="ap-northeast-2",
        timeout_seconds=7,
        max_output_tokens=2048,
    )

    assert captured["service_name"] == "bedrock-runtime"
    assert captured["region_name"] == "ap-northeast-2"
    config = captured["config"]
    assert config.connect_timeout == 7
    assert config.read_timeout == 7
    assert config.retries["total_max_attempts"] == 1


@pytest.mark.asyncio
async def test_bedrock_request_matches_installed_botocore_converse_model() -> None:
    import botocore.session
    from botocore.stub import Stubber

    command = _teacher_command()
    content = await _valid_content(command)
    client = botocore.session.get_session().create_client(
        "bedrock-runtime",
        region_name="ap-northeast-2",
        aws_access_key_id="test-access-key",
        aws_secret_access_key="test-secret-key",
    )
    stubber = Stubber(client)
    stubber.add_response(
        "converse",
        {
            "output": {
                "message": {
                    "role": "assistant",
                    "content": [{"text": content}],
                }
            },
            "stopReason": "end_turn",
            "usage": {
                "inputTokens": 31,
                "outputTokens": 17,
                "totalTokens": 48,
            },
            "metrics": {"latencyMs": 10},
        },
    )
    provider = BedrockGenerationProvider(
        model_id="bedrock-model",
        region_name="ap-northeast-2",
        timeout_seconds=3,
        max_output_tokens=2048,
        client=client,
    )

    with stubber:
        result = await provider.generate(command)

    assert result.content == content


@pytest.mark.asyncio
async def test_openai_request_matches_installed_sdk_responses_model() -> None:
    import httpx
    from openai import AsyncOpenAI

    command = _teacher_command()
    content = await _valid_content(command)
    captured: dict[str, Any] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        captured.update(json.loads(request.content))
        return httpx.Response(
            200,
            request=request,
            headers={"x-request-id": "req_test"},
            json={
                "id": "resp_test",
                "created_at": 0,
                "model": "openai-model",
                "object": "response",
                "output": [
                    {
                        "id": "msg_test",
                        "content": [
                            {
                                "annotations": [],
                                "text": content,
                                "type": "output_text",
                            }
                        ],
                        "role": "assistant",
                        "status": "completed",
                        "type": "message",
                    }
                ],
                "parallel_tool_calls": False,
                "status": "completed",
                "tool_choice": "none",
                "tools": [],
                "usage": {
                    "input_tokens": 29,
                    "input_tokens_details": {
                        "cache_write_tokens": 0,
                        "cached_tokens": 0,
                    },
                    "output_tokens": 13,
                    "output_tokens_details": {"reasoning_tokens": 0},
                    "total_tokens": 42,
                },
            },
        )

    http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    client = AsyncOpenAI(
        api_key="test-key",
        base_url="https://api.openai.test/v1",
        http_client=http_client,
        max_retries=0,
    )
    provider = OpenAIGenerationProvider(
        model_id="openai-model",
        timeout_seconds=3,
        max_output_tokens=2048,
        client=client,
    )
    try:
        result = await provider.generate(command)
    finally:
        await client.close()

    assert result.content == content
    assert captured["instructions"] == command.system_instruction
    assert captured["input"] == command.trusted_input_json
    assert captured["text"]["format"]["type"] == "json_schema"
    assert captured["text"]["format"]["strict"] is True
    assert captured["store"] is False
