from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from typing import Any

from pydantic import ValidationError

from bibim_ai.config import Settings
from bibim_ai.errors import (
    ProviderRateLimitedError,
    ProviderUnavailableError,
    ServiceError,
)
from bibim_ai.models import (
    BalancingGenerationRequest,
    BalancingGenerationResponse,
    BalancingOutput,
    EconomicEventGenerationRequest,
    EconomicEventGenerationResponse,
    EconomicEventOutput,
    ErrorCode,
    GenerationOutput,
    GenerationRequest,
    GenerationResponse,
    QuizDraftGenerationRequest,
    QuizDraftGenerationResponse,
    QuizDraftOutput,
    TeacherChatGenerationRequest,
    TeacherChatGenerationResponse,
    TeacherChatOutput,
)
from bibim_ai.provider import GenerationKind, GenerationProvider
from bibim_ai.validation import validate_input_policy, validate_output_policy

OUTPUT_MODELS: dict[GenerationKind, type[GenerationOutput]] = {
    GenerationKind.BALANCING: BalancingOutput,
    GenerationKind.TEACHER_CHAT: TeacherChatOutput,
    GenerationKind.ECONOMIC_EVENT: EconomicEventOutput,
    GenerationKind.QUIZ_DRAFT: QuizDraftOutput,
}


class GenerationService:
    def __init__(self, *, settings: Settings, provider: GenerationProvider) -> None:
        self._settings = settings
        self._provider = provider

    async def generate(
        self, kind: GenerationKind, request: GenerationRequest
    ) -> GenerationResponse:
        validate_input_policy(request)
        timeout = self._provider_timeout(request)

        try:
            provider_result = await asyncio.wait_for(
                self._provider.generate(kind, request), timeout=timeout
            )
        except TimeoutError as error:
            raise ServiceError(
                status_code=504,
                code=ErrorCode.DEADLINE_EXCEEDED,
                message="AI 생성 제한시간을 초과했습니다.",
                validation_codes=["PROVIDER_TIMEOUT"],
            ) from error
        except ProviderRateLimitedError as error:
            raise ServiceError(
                status_code=429,
                code=ErrorCode.PROVIDER_RATE_LIMITED,
                message="AI 공급자 호출이 일시적으로 제한되었습니다.",
                retryable=self._upstream_may_retry(request),
                validation_codes=["PROVIDER_RATE_LIMITED"],
            ) from error
        except ProviderUnavailableError as error:
            raise ServiceError(
                status_code=503,
                code=ErrorCode.PROVIDER_UNAVAILABLE,
                message="AI 공급자를 일시적으로 사용할 수 없습니다.",
                retryable=self._upstream_may_retry(request),
                validation_codes=["PROVIDER_UNAVAILABLE"],
            ) from error

        output = self._validate_provider_output(kind, provider_result.output)
        validate_output_policy(request, output)
        metadata: dict[str, Any] = {
            "ai_run_id": request.ai_run_id,
            "schema_version": request.schema_version,
            "prompt_version": request.prompt_version,
            "provider": self._settings.provider_name,
            "model_id": self._settings.provider_model_id,
            "safety_status": "PASSED",
            "validation_codes": [],
            "usage": provider_result.usage,
            "output": output,
        }
        return self._build_response(request, metadata)

    def _provider_timeout(self, request: GenerationRequest) -> float:
        remaining = (request.deadline_at - datetime.now(UTC)).total_seconds()
        if remaining <= self._settings.minimum_deadline_remaining_seconds:
            raise ServiceError(
                status_code=504,
                code=ErrorCode.DEADLINE_EXCEEDED,
                message="AI 생성 제한시간이 이미 지났거나 남은 시간이 부족합니다.",
                validation_codes=["DEADLINE_NOT_ENOUGH"],
            )
        return min(remaining, self._settings.provider_timeout_seconds)

    def _upstream_may_retry(self, request: GenerationRequest) -> bool:
        remaining = (request.deadline_at - datetime.now(UTC)).total_seconds()
        return (
            request.attempt == 1 and remaining > self._settings.minimum_deadline_remaining_seconds
        )

    @staticmethod
    def _validate_provider_output(
        kind: GenerationKind, raw_output: dict[str, Any]
    ) -> GenerationOutput:
        model = OUTPUT_MODELS[kind]
        try:
            return model.model_validate(raw_output)
        except ValidationError as error:
            codes = [
                "OUTPUT_" + "_".join(str(part).upper() for part in item["loc"])
                for item in error.errors(include_input=False, include_url=False)
            ]
            raise ServiceError(
                status_code=422,
                code=ErrorCode.OUTPUT_SCHEMA_INVALID,
                message="AI 출력이 구조화 응답 계약을 충족하지 않습니다.",
                validation_codes=codes or ["OUTPUT_SCHEMA_INVALID"],
            ) from error

    @staticmethod
    def _build_response(request: GenerationRequest, metadata: dict[str, Any]) -> GenerationResponse:
        if isinstance(request, BalancingGenerationRequest):
            return BalancingGenerationResponse.model_validate(metadata)
        if isinstance(request, TeacherChatGenerationRequest):
            return TeacherChatGenerationResponse.model_validate(metadata)
        if isinstance(request, EconomicEventGenerationRequest):
            return EconomicEventGenerationResponse.model_validate(metadata)
        if isinstance(request, QuizDraftGenerationRequest):
            return QuizDraftGenerationResponse.model_validate(metadata)
        raise TypeError("지원하지 않는 생성 요청입니다.")
