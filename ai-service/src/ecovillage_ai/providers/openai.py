from __future__ import annotations

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


class OpenAIGenerationProvider:
    provider_name = "OPENAI"

    def __init__(
        self,
        *,
        model_id: str,
        timeout_seconds: float,
        max_output_tokens: int,
        client: Any | None = None,
    ) -> None:
        self._model_id = model_id
        self._max_output_tokens = max_output_tokens
        self._client = client or self._build_client(timeout_seconds)

    @staticmethod
    def _build_client(timeout_seconds: float) -> Any:
        try:
            from openai import AsyncOpenAI
        except ImportError as error:
            raise ProviderConfigurationError(
                "OpenAI SDK가 설치되지 않았습니다."
            ) from error
        return AsyncOpenAI(timeout=timeout_seconds, max_retries=0)

    def provider_name_for(self, kind: GenerationKind) -> str:
        return self.provider_name

    def model_id_for(self, kind: GenerationKind) -> str:
        return self._model_id

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        try:
            response = await self._client.responses.create(
                model=self._model_id,
                instructions=command.system_instruction,
                input=command.trusted_input_json,
                text={
                    "format": {
                        "type": "json_schema",
                        "name": schema_name(command.prompt_key),
                        "description": "이코빌리지 내부 AI 구조화 생성 결과",
                        "strict": True,
                        "schema": strict_output_schema(command.output_schema),
                    }
                },
                max_output_tokens=self._max_output_tokens,
                store=False,
            )
            content = self._response_text(response)
            usage = self._usage(response)
        except ProviderError:
            raise
        except Exception as error:
            raise self._map_error(error) from error
        return ProviderResult(content=content, usage=usage)

    @staticmethod
    def _response_text(response: Any) -> str:
        status = getattr(response, "status", None)
        if status not in {None, "completed"}:
            raise ProviderError("OpenAI 응답이 완료되지 않았습니다.")
        content = getattr(response, "output_text", None)
        if not isinstance(content, str) or not content.strip():
            raise ProviderError("OpenAI 구조화 응답 본문이 없습니다.")
        return content

    @staticmethod
    def _usage(response: Any) -> Usage:
        usage = getattr(response, "usage", None)
        return Usage(
            input_tokens=max(0, int(getattr(usage, "input_tokens", 0))),
            output_tokens=max(0, int(getattr(usage, "output_tokens", 0))),
        )

    @staticmethod
    def _map_error(error: Exception) -> ProviderError:
        name = type(error).__name__
        status = getattr(error, "status_code", None)
        if name == "RateLimitError" or status == 429:
            return ProviderRateLimitedError("OpenAI 호출이 제한되었습니다.")
        if name == "APITimeoutError" or status == 408:
            return ProviderTimeoutError("OpenAI 호출 제한시간을 초과했습니다.")
        if name in {
            "AuthenticationError",
            "BadRequestError",
            "NotFoundError",
            "PermissionDeniedError",
            "UnprocessableEntityError",
        } or status in {400, 401, 403, 404, 422}:
            return ProviderConfigurationError("OpenAI 호출 설정이 올바르지 않습니다.")
        return ProviderUnavailableError("OpenAI를 일시적으로 사용할 수 없습니다.")
