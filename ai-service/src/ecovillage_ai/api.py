from __future__ import annotations

import secrets
from collections.abc import Callable
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.models import (
    BalancingGenerationRequest,
    BalancingGenerationResponse,
    EconomicEventGenerationRequest,
    EconomicEventGenerationResponse,
    ErrorCode,
    ErrorResponse,
    QuizDraftGenerationRequest,
    QuizDraftGenerationResponse,
    TeacherChatGenerationRequest,
    TeacherChatGenerationResponse,
)
from ecovillage_ai.provider import GenerationKind

router = APIRouter(prefix="/internal/v1/generations", tags=["Internal AI Generations"])
bearer = HTTPBearer(
    auto_error=False,
    scheme_name="internalServiceBearer",
    description="기능별 ai:generate:* scope가 포함된 내부 서비스 토큰",
)

ERROR_RESPONSES = {
    400: {"model": ErrorResponse, "description": "입력 계약 위반"},
    403: {"model": ErrorResponse, "description": "내부 서비스 scope 위반"},
    409: {"model": ErrorResponse, "description": "AI 실행 문맥 불일치"},
    422: {"model": ErrorResponse, "description": "충분성·출력 검증 실패"},
    429: {"model": ErrorResponse, "description": "공급자 호출 제한"},
    503: {"model": ErrorResponse, "description": "공급자 일시 장애"},
    504: {"model": ErrorResponse, "description": "절대 기한 초과"},
}


def require_scope(required_scope: str) -> Callable[..., None]:
    async def dependency(
        request: Request,
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    ) -> None:
        settings = request.app.state.settings
        token_matches = credentials is not None and secrets.compare_digest(
            credentials.credentials, settings.internal_service_token
        )
        if (
            credentials is None
            or credentials.scheme.lower() != "bearer"
            or not token_matches
            or required_scope not in settings.internal_service_scopes
        ):
            raise ServiceError(
                status_code=403,
                code=ErrorCode.SERVICE_SCOPE_VIOLATION,
                message="내부 AI 서비스 호출 권한이 없습니다.",
                validation_codes=["REQUIRED_SCOPE_MISSING"],
            )

    return dependency


def validate_context(
    body_ai_run_id: UUID,
    body_correlation_id: UUID,
    header_ai_run_id: UUID,
    header_correlation_id: UUID,
) -> None:
    if body_ai_run_id != header_ai_run_id or body_correlation_id != header_correlation_id:
        raise ServiceError(
            status_code=409,
            code=ErrorCode.RUN_CONTEXT_MISMATCH,
            message="AI 실행 ID 또는 상관관계 ID가 헤더와 본문에서 일치하지 않습니다.",
            validation_codes=["HEADER_BODY_CONTEXT_MISMATCH"],
        )


CorrelationHeader = Annotated[UUID, Header(alias="X-Correlation-Id")]
AiRunHeader = Annotated[UUID, Header(alias="X-AI-Run-Id")]


@router.post(
    "/balancing",
    response_model=BalancingGenerationResponse,
    responses=ERROR_RESPONSES,
    operation_id="generateBalancingProposal",
    dependencies=[Depends(require_scope("ai:generate:balancing"))],
    openapi_extra={"x-required-scope": "ai:generate:balancing"},
)
async def generate_balancing(
    body: BalancingGenerationRequest,
    request: Request,
    correlation_id: CorrelationHeader,
    ai_run_id: AiRunHeader,
) -> BalancingGenerationResponse:
    validate_context(body.ai_run_id, body.correlation_id, ai_run_id, correlation_id)
    return await request.app.state.generation_service.generate(GenerationKind.BALANCING, body)


@router.post(
    "/teacher-chat",
    response_model=TeacherChatGenerationResponse,
    responses=ERROR_RESPONSES,
    operation_id="generateTeacherChatAnswer",
    dependencies=[Depends(require_scope("ai:generate:teacher-chat"))],
    openapi_extra={
        "x-required-scope": "ai:generate:teacher-chat",
        "x-total-timeout-seconds": 20,
    },
)
async def generate_teacher_chat(
    body: TeacherChatGenerationRequest,
    request: Request,
    correlation_id: CorrelationHeader,
    ai_run_id: AiRunHeader,
) -> TeacherChatGenerationResponse:
    validate_context(body.ai_run_id, body.correlation_id, ai_run_id, correlation_id)
    return await request.app.state.generation_service.generate(GenerationKind.TEACHER_CHAT, body)


@router.post(
    "/economic-event",
    response_model=EconomicEventGenerationResponse,
    responses=ERROR_RESPONSES,
    operation_id="generateEconomicEventDraft",
    dependencies=[Depends(require_scope("ai:generate:economic-event"))],
    openapi_extra={"x-required-scope": "ai:generate:economic-event"},
)
async def generate_economic_event(
    body: EconomicEventGenerationRequest,
    request: Request,
    correlation_id: CorrelationHeader,
    ai_run_id: AiRunHeader,
) -> EconomicEventGenerationResponse:
    validate_context(body.ai_run_id, body.correlation_id, ai_run_id, correlation_id)
    return await request.app.state.generation_service.generate(GenerationKind.ECONOMIC_EVENT, body)


@router.post(
    "/quiz-draft",
    response_model=QuizDraftGenerationResponse,
    responses=ERROR_RESPONSES,
    operation_id="generateQuizDraft",
    dependencies=[Depends(require_scope("ai:generate:quiz-draft"))],
    openapi_extra={"x-required-scope": "ai:generate:quiz-draft"},
)
async def generate_quiz_draft(
    body: QuizDraftGenerationRequest,
    request: Request,
    correlation_id: CorrelationHeader,
    ai_run_id: AiRunHeader,
) -> QuizDraftGenerationResponse:
    validate_context(body.ai_run_id, body.correlation_id, ai_run_id, correlation_id)
    return await request.app.state.generation_service.generate(GenerationKind.QUIZ_DRAFT, body)
