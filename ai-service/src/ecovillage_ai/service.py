from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from time import perf_counter
from typing import Any

from ecovillage_ai.config import Settings
from ecovillage_ai.errors import (
    ProviderError,
    ProviderRateLimitedError,
    ProviderUnavailableError,
    ServiceError,
)
from ecovillage_ai.models import (
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
    SetupPrecheckOutput,
    SetupPrecheckPayload,
    TeacherChatGenerationRequest,
    TeacherChatGenerationResponse,
    TeacherChatOutput,
    Usage,
)
from ecovillage_ai.prompting import PromptRegistry, PromptRegistryError
from ecovillage_ai.provider import GenerationProvider
from ecovillage_ai.runtime import GenerationCommand, GenerationKind, ProviderResult
from ecovillage_ai.telemetry import LoggingRunEventSink, RunEventSink, SafeRunEvent
from ecovillage_ai.validation import OutputValidationPipeline, validate_input_policy

OUTPUT_MODELS: dict[GenerationKind, type[GenerationOutput]] = {
    GenerationKind.TEACHER_CHAT: TeacherChatOutput,
    GenerationKind.ECONOMIC_EVENT: EconomicEventOutput,
    GenerationKind.QUIZ_DRAFT: QuizDraftOutput,
}


def output_model_for(
    kind: GenerationKind, request: GenerationRequest
) -> type[GenerationOutput]:
    if kind is GenerationKind.BALANCING:
        if not isinstance(request, BalancingGenerationRequest):
            raise TypeError("밸런싱 생성에는 밸런싱 요청이 필요합니다.")
        return (
            SetupPrecheckOutput
            if isinstance(request.payload, SetupPrecheckPayload)
            else BalancingOutput
        )
    return OUTPUT_MODELS[kind]


class GenerationService:
    def __init__(
        self,
        *,
        settings: Settings,
        provider: GenerationProvider,
        prompt_registry: PromptRegistry | None = None,
        validation_pipeline: OutputValidationPipeline | None = None,
        event_sink: RunEventSink | None = None,
    ) -> None:
        self._settings = settings
        self._provider = provider
        self._prompt_registry = prompt_registry or PromptRegistry()
        self._validation_pipeline = validation_pipeline or OutputValidationPipeline()
        self._event_sink = event_sink or LoggingRunEventSink()

    async def generate(
        self, kind: GenerationKind, request: GenerationRequest
    ) -> GenerationResponse:
        started_at = perf_counter()
        command: GenerationCommand | None = None
        usage = Usage(input_tokens=0, output_tokens=0)
        try:
            validate_input_policy(request)
            output_model = output_model_for(kind, request)
            command = self._build_command(kind, request, output_model)
            provider_result = await self._call_provider(command, request)
            usage = provider_result.usage
            output = self._validation_pipeline.validate(
                request, output_model, provider_result.content
            )
            metadata: dict[str, Any] = {
                "ai_run_id": request.ai_run_id,
                "schema_version": request.schema_version,
                "prompt_version": request.prompt_version,
                "provider": self._settings.provider_name,
                "model_id": self._settings.provider_model_id,
                "safety_status": "PASSED",
                "validation_codes": [],
                "usage": usage,
                "output": output,
            }
            response = self._build_response(request, metadata)
        except ServiceError as error:
            self._record_event(
                kind=kind,
                request=request,
                command=command,
                usage=usage,
                started_at=started_at,
                outcome="FAILED",
                error=error,
            )
            raise
        self._record_event(
            kind=kind,
            request=request,
            command=command,
            usage=usage,
            started_at=started_at,
            outcome="SUCCEEDED",
            error=None,
        )
        return response

    def _build_command(
        self,
        kind: GenerationKind,
        request: GenerationRequest,
        output_model: type[GenerationOutput],
    ) -> GenerationCommand:
        try:
            return self._prompt_registry.build_command(kind, request, output_model)
        except PromptRegistryError as error:
            raise ServiceError(
                status_code=409,
                code=ErrorCode.RUN_CONTEXT_MISMATCH,
                message=error.safe_message,
                validation_codes=[error.code],
                validation_stage="CONTEXT",
            ) from error

    async def _call_provider(
        self, command: GenerationCommand, request: GenerationRequest
    ) -> ProviderResult:
        timeout = self._provider_timeout(request)
        try:
            return await asyncio.wait_for(self._provider.generate(command), timeout=timeout)
        except TimeoutError as error:
            raise ServiceError(
                status_code=504,
                code=ErrorCode.DEADLINE_EXCEEDED,
                message="AI 생성 제한시간을 초과했습니다.",
                validation_codes=["PROVIDER_TIMEOUT"],
                validation_stage="PROVIDER",
            ) from error
        except ProviderRateLimitedError as error:
            raise ServiceError(
                status_code=429,
                code=ErrorCode.PROVIDER_RATE_LIMITED,
                message="AI 공급자 호출이 일시적으로 제한되었습니다.",
                retryable=self._upstream_may_retry(request),
                validation_codes=["PROVIDER_RATE_LIMITED"],
                validation_stage="PROVIDER",
            ) from error
        except ProviderUnavailableError as error:
            raise ServiceError(
                status_code=503,
                code=ErrorCode.PROVIDER_UNAVAILABLE,
                message="AI 공급자를 일시적으로 사용할 수 없습니다.",
                retryable=self._upstream_may_retry(request),
                validation_codes=["PROVIDER_UNAVAILABLE"],
                validation_stage="PROVIDER",
            ) from error
        except ProviderError as error:
            raise ServiceError(
                status_code=503,
                code=ErrorCode.PROVIDER_UNAVAILABLE,
                message="AI 공급자 응답을 처리할 수 없습니다.",
                retryable=False,
                validation_codes=["PROVIDER_ERROR"],
                validation_stage="PROVIDER",
            ) from error
        except Exception as error:
            raise ServiceError(
                status_code=503,
                code=ErrorCode.PROVIDER_UNAVAILABLE,
                message="AI 공급자를 일시적으로 사용할 수 없습니다.",
                retryable=False,
                validation_codes=["PROVIDER_UNEXPECTED_ERROR"],
                validation_stage="PROVIDER",
            ) from error

    def _provider_timeout(self, request: GenerationRequest) -> float:
        remaining = (request.deadline_at - datetime.now(UTC)).total_seconds()
        if remaining <= self._settings.minimum_deadline_remaining_seconds:
            raise ServiceError(
                status_code=504,
                code=ErrorCode.DEADLINE_EXCEEDED,
                message="AI 생성 제한시간이 이미 지났거나 남은 시간이 부족합니다.",
                validation_codes=["DEADLINE_NOT_ENOUGH"],
                validation_stage="DEADLINE",
            )
        return min(remaining, self._settings.provider_timeout_seconds)

    def _upstream_may_retry(self, request: GenerationRequest) -> bool:
        remaining = (request.deadline_at - datetime.now(UTC)).total_seconds()
        return (
            request.attempt == 1
            and remaining > self._settings.minimum_deadline_remaining_seconds
        )

    def _record_event(
        self,
        *,
        kind: GenerationKind,
        request: GenerationRequest,
        command: GenerationCommand | None,
        usage: Usage,
        started_at: float,
        outcome: str,
        error: ServiceError | None,
    ) -> None:
        event = SafeRunEvent(
            ai_run_id=str(request.ai_run_id),
            request_id=str(request.request_id),
            correlation_id=str(request.correlation_id),
            kind=kind.value,
            schema_version=request.schema_version,
            prompt_version=request.prompt_version,
            prompt_key=command.prompt_key if command is not None else None,
            provider=self._settings.provider_name,
            model_id=self._settings.provider_model_id,
            attempt=request.attempt,
            grade=request.grade,
            duration_ms=max(0, round((perf_counter() - started_at) * 1000)),
            input_tokens=usage.input_tokens,
            output_tokens=usage.output_tokens,
            outcome=outcome,
            error_code=error.code.value if error is not None else None,
            validation_stage=error.validation_stage if error is not None else None,
            validation_codes=tuple(error.validation_codes) if error is not None else (),
        )
        try:
            self._event_sink.record(event)
        except Exception:
            try:
                LoggingRunEventSink().record(
                    SafeRunEvent(
                        **{
                            **event.as_dict(),
                            "outcome": "TELEMETRY_FAILED",
                            "error_code": "SAFE_RUN_EVENT_SINK_FAILED",
                        }
                    )
                )
            except Exception:
                return

    @staticmethod
    def _build_response(
        request: GenerationRequest, metadata: dict[str, Any]
    ) -> GenerationResponse:
        if isinstance(request, BalancingGenerationRequest):
            return BalancingGenerationResponse.model_validate(metadata)
        if isinstance(request, TeacherChatGenerationRequest):
            return TeacherChatGenerationResponse.model_validate(metadata)
        if isinstance(request, EconomicEventGenerationRequest):
            return EconomicEventGenerationResponse.model_validate(metadata)
        if isinstance(request, QuizDraftGenerationRequest):
            return QuizDraftGenerationResponse.model_validate(metadata)
        raise TypeError("지원하지 않는 생성 요청입니다.")
