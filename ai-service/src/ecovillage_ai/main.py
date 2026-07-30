from __future__ import annotations

from collections.abc import Awaitable, Callable
from uuid import UUID, uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError, ResponseValidationError
from fastapi.responses import JSONResponse
from starlette.responses import Response

from ecovillage_ai.api import router
from ecovillage_ai.config import Settings
from ecovillage_ai.errors import ServiceError
from ecovillage_ai.models import ErrorCode, ErrorDetail, ErrorResponse, HealthResponse
from ecovillage_ai.prompting import PromptRegistry
from ecovillage_ai.provider import GenerationProvider, build_generation_provider
from ecovillage_ai.service import GenerationService
from ecovillage_ai.telemetry import RunEventSink
from ecovillage_ai.validation import OutputValidationPipeline


def _header_uuid(request: Request, header_name: str) -> UUID:
    try:
        return UUID(request.headers.get(header_name, ""))
    except (TypeError, ValueError):
        return uuid4()


def _error_json(request: Request, error: ServiceError) -> dict[str, object]:
    body = ErrorResponse(
        error=ErrorDetail(
            code=error.code,
            message=error.safe_message,
            retryable=error.retryable,
            validation_codes=error.validation_codes,
        ),
        ai_run_id=request.state.ai_run_id,
        correlation_id=request.state.correlation_id,
    )
    return body.model_dump(mode="json")


def create_app(
    *,
    settings: Settings | None = None,
    provider: GenerationProvider | None = None,
    prompt_registry: PromptRegistry | None = None,
    validation_pipeline: OutputValidationPipeline | None = None,
    event_sink: RunEventSink | None = None,
) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    resolved_provider = provider or build_generation_provider(resolved_settings)
    app = FastAPI(
        title=resolved_settings.app_name,
        version=resolved_settings.app_version,
        docs_url="/docs" if resolved_settings.docs_enabled else None,
        redoc_url=None,
        openapi_url="/openapi.json" if resolved_settings.docs_enabled else None,
    )
    app.state.settings = resolved_settings
    app.state.generation_service = GenerationService(
        settings=resolved_settings,
        provider=resolved_provider,
        prompt_registry=prompt_registry,
        validation_pipeline=validation_pipeline,
        event_sink=event_sink,
    )

    @app.middleware("http")
    async def attach_request_context(
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        request.state.correlation_id = _header_uuid(request, "X-Correlation-Id")
        request.state.ai_run_id = _header_uuid(request, "X-AI-Run-Id")
        response = await call_next(request)
        response.headers["X-Correlation-Id"] = str(request.state.correlation_id)
        response.headers["X-AI-Run-Id"] = str(request.state.ai_run_id)
        return response

    @app.exception_handler(ServiceError)
    async def handle_service_error(request: Request, error: ServiceError) -> JSONResponse:
        return JSONResponse(status_code=error.status_code, content=_error_json(request, error))

    @app.exception_handler(RequestValidationError)
    async def handle_request_validation(
        request: Request, error: RequestValidationError
    ) -> JSONResponse:
        codes = [
            "INPUT_" + "_".join(str(part).upper() for part in item["loc"])
            for item in error.errors()
        ]
        service_error = ServiceError(
            status_code=400,
            code=ErrorCode.INPUT_SCHEMA_INVALID,
            message="AI 생성 요청이 입력 계약을 충족하지 않습니다.",
            validation_codes=codes or ["INPUT_SCHEMA_INVALID"],
        )
        return JSONResponse(status_code=400, content=_error_json(request, service_error))

    @app.exception_handler(ResponseValidationError)
    async def handle_response_validation(
        request: Request, error: ResponseValidationError
    ) -> JSONResponse:
        service_error = ServiceError(
            status_code=422,
            code=ErrorCode.OUTPUT_SCHEMA_INVALID,
            message="AI 출력이 구조화 응답 계약을 충족하지 않습니다.",
            validation_codes=["RESPONSE_SCHEMA_INVALID"],
        )
        return JSONResponse(status_code=422, content=_error_json(request, service_error))

    @app.exception_handler(Exception)
    async def handle_unexpected_error(request: Request, error: Exception) -> JSONResponse:
        service_error = ServiceError(
            status_code=503,
            code=ErrorCode.PROVIDER_UNAVAILABLE,
            message="AI 생성 서비스를 일시적으로 사용할 수 없습니다.",
            validation_codes=["UNEXPECTED_SERVICE_ERROR"],
        )
        return JSONResponse(status_code=503, content=_error_json(request, service_error))

    @app.get("/healthz", response_model=HealthResponse, tags=["Health"])
    async def health() -> HealthResponse:
        return HealthResponse(
            status="UP",
            service=resolved_settings.app_name,
            version=resolved_settings.app_version,
            provider=resolved_settings.provider_name,
        )

    app.include_router(router)
    return app


app = create_app()
