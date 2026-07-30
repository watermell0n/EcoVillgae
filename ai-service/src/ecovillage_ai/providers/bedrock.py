from __future__ import annotations

import asyncio
import json
from collections.abc import Mapping
from typing import Any

from ecovillage_ai.errors import (
    ProviderConfigurationError,
    ProviderError,
    ProviderRateLimitedError,
    ProviderTimeoutError,
    ProviderUnavailableError,
)
from ecovillage_ai.models import Usage
from ecovillage_ai.providers.schema import schema_name, strict_output_schema
from ecovillage_ai.runtime import GenerationCommand, GenerationKind, ProviderResult


class BedrockGenerationProvider:
    provider_name = "BEDROCK"

    def __init__(
        self,
        *,
        model_id: str,
        region_name: str,
        timeout_seconds: float,
        max_output_tokens: int,
        client: Any | None = None,
    ) -> None:
        self._model_id = model_id
        self._max_output_tokens = max_output_tokens
        self._client = client or self._build_client(region_name, timeout_seconds)

    @staticmethod
    def _build_client(region_name: str, timeout_seconds: float) -> Any:
        try:
            import boto3
            from botocore.config import Config
        except ImportError as error:
            raise ProviderConfigurationError(
                "Bedrock SDK가 설치되지 않았습니다."
            ) from error
        config = Config(
            connect_timeout=timeout_seconds,
            read_timeout=timeout_seconds,
            retries={"total_max_attempts": 1, "mode": "standard"},
        )
        return boto3.client(
            "bedrock-runtime",
            region_name=region_name,
            config=config,
        )

    def provider_name_for(self, kind: GenerationKind) -> str:
        return self.provider_name

    def model_id_for(self, kind: GenerationKind) -> str:
        return self._model_id

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        request = {
            "modelId": self._model_id,
            "system": [{"text": command.system_instruction}],
            "messages": [
                {
                    "role": "user",
                    "content": [{"text": command.trusted_input_json}],
                }
            ],
            "inferenceConfig": {"maxTokens": self._max_output_tokens},
            "outputConfig": {
                "textFormat": {
                    "type": "json_schema",
                    "structure": {
                        "jsonSchema": {
                            "schema": json.dumps(
                                strict_output_schema(command.output_schema),
                                ensure_ascii=False,
                                sort_keys=True,
                                separators=(",", ":"),
                            ),
                            "name": schema_name(command.prompt_key),
                            "description": "이코빌리지 내부 AI 구조화 생성 결과",
                        }
                    },
                }
            },
        }
        try:
            response = await asyncio.to_thread(self._client.converse, **request)
            content = self._response_text(response)
            usage = self._usage(response)
        except ProviderError:
            raise
        except Exception as error:
            raise self._map_error(error) from error
        return ProviderResult(content=content, usage=usage)

    @staticmethod
    def _response_text(response: Any) -> str:
        if not isinstance(response, Mapping):
            raise ProviderError("Bedrock 응답 형식이 올바르지 않습니다.")
        output = response.get("output")
        message = output.get("message") if isinstance(output, Mapping) else None
        blocks = message.get("content") if isinstance(message, Mapping) else None
        if not isinstance(blocks, list):
            raise ProviderError("Bedrock 응답 본문이 없습니다.")
        text_parts = [
            block["text"]
            for block in blocks
            if isinstance(block, Mapping)
            and isinstance(block.get("text"), str)
            and block["text"].strip()
        ]
        if not text_parts:
            raise ProviderError("Bedrock 구조화 응답 본문이 없습니다.")
        return "".join(text_parts)

    @staticmethod
    def _usage(response: Mapping[str, Any]) -> Usage:
        usage = response.get("usage")
        if not isinstance(usage, Mapping):
            return Usage(input_tokens=0, output_tokens=0)
        return Usage(
            input_tokens=max(0, int(usage.get("inputTokens", 0))),
            output_tokens=max(0, int(usage.get("outputTokens", 0))),
        )

    @staticmethod
    def _map_error(error: Exception) -> ProviderError:
        response = getattr(error, "response", None)
        error_body = response.get("Error", {}) if isinstance(response, Mapping) else {}
        code = str(error_body.get("Code", type(error).__name__))
        status = (
            response.get("ResponseMetadata", {}).get("HTTPStatusCode")
            if isinstance(response, Mapping)
            else None
        )

        if code in {
            "ThrottlingException",
            "TooManyRequestsException",
            "ServiceQuotaExceededException",
            "ModelNotReadyException",
        } or status == 429:
            return ProviderRateLimitedError("Bedrock 호출이 제한되었습니다.")
        if code in {
            "ModelTimeoutException",
            "ReadTimeoutError",
            "ConnectTimeoutError",
        } or status == 408:
            return ProviderTimeoutError("Bedrock 호출 제한시간을 초과했습니다.")
        if code in {
            "AccessDeniedException",
            "ResourceNotFoundException",
            "ValidationException",
            "UnrecognizedClientException",
        } or status in {400, 401, 403, 404}:
            return ProviderConfigurationError("Bedrock 호출 설정이 올바르지 않습니다.")
        return ProviderUnavailableError("Bedrock을 일시적으로 사용할 수 없습니다.")
