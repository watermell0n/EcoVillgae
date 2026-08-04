from __future__ import annotations

import os
import time

import pytest

from ecovillage_ai.config import Settings
from ecovillage_ai.models import (
    BalancingGenerationRequest,
    BalancingOutput,
    EconomicEventGenerationRequest,
    EconomicEventOutput,
    QuizDraftGenerationRequest,
    QuizDraftOutput,
    SetupPrecheckOutput,
    TeacherChatGenerationRequest,
    TeacherChatOutput,
)
from ecovillage_ai.prompting import PromptRegistry
from ecovillage_ai.provider import build_generation_provider
from ecovillage_ai.runtime import GenerationKind
from ecovillage_ai.validation import OutputValidationPipeline
from tests.factories import (
    balancing_request,
    economic_event_request,
    quiz_draft_request,
    setup_precheck_request,
    teacher_chat_request,
)

SMOKE_ENABLED = os.getenv("ECOVILLAGE_AI_RUN_PROVIDER_SMOKE", "").lower() in {
    "1",
    "true",
    "yes",
}

REAL_PROVIDERS = {"BEDROCK", "OPENAI"}

# 지연 경고선. 공급자 타임아웃 10초의 절반을 넘으면 경고하고 두 배를 넘으면 실패로 본다.
LATENCY_WARNING_SECONDS = 5.0

# (사례 이름, 생성 기능, 요청 모델, 요청 payload 팩토리, 출력 모델)
SMOKE_CASES = [
    (
        "setup-precheck",
        GenerationKind.BALANCING,
        BalancingGenerationRequest,
        setup_precheck_request,
        SetupPrecheckOutput,
    ),
    (
        "balancing",
        GenerationKind.BALANCING,
        BalancingGenerationRequest,
        balancing_request,
        BalancingOutput,
    ),
    (
        "teacher-chat",
        GenerationKind.TEACHER_CHAT,
        TeacherChatGenerationRequest,
        teacher_chat_request,
        TeacherChatOutput,
    ),
    (
        "economic-event",
        GenerationKind.ECONOMIC_EVENT,
        EconomicEventGenerationRequest,
        economic_event_request,
        EconomicEventOutput,
    ),
    (
        "quiz-draft",
        GenerationKind.QUIZ_DRAFT,
        QuizDraftGenerationRequest,
        quiz_draft_request,
        QuizDraftOutput,
    ),
]


def _require_real_provider(kind: GenerationKind) -> Settings:
    settings = Settings.from_env()
    route = settings.route_for(kind.value)
    assert route.provider_name in REAL_PROVIDERS, (
        f"{kind.value}이 FAKE로 설정되어 있습니다. 실제 공급자 smoke를 돌리려면 "
        "ECOVILLAGE_AI_PROVIDER나 기능별 라우팅을 실제 공급자로 지정해야 합니다."
    )
    return settings


@pytest.mark.provider_smoke
@pytest.mark.skipif(
    not SMOKE_ENABLED,
    reason="실제 공급자 smoke는 ECOVILLAGE_AI_RUN_PROVIDER_SMOKE=true에서만 실행합니다.",
)
@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("case_name", "kind", "request_model", "payload_factory", "output_model"),
    SMOKE_CASES,
    ids=[case[0] for case in SMOKE_CASES],
)
async def test_real_provider_follows_generation_contract(
    case_name: str,
    kind: GenerationKind,
    request_model: type,
    payload_factory,
    output_model: type,
) -> None:
    """다섯 생성 기능이 실제 공급자에서도 계약과 안전 경계를 지키는지 확인한다.

    지연과 토큰 사용량을 함께 출력해 모델 선택과 크레딧 판정 근거로 쓴다.
    """
    settings = _require_real_provider(kind)
    route = settings.route_for(kind.value)
    provider = build_generation_provider(settings)
    request = request_model.model_validate(payload_factory())
    command = PromptRegistry().build_command(kind, request, output_model)

    started = time.perf_counter()
    result = await provider.generate(command)
    elapsed = time.perf_counter() - started

    output = OutputValidationPipeline().validate(request, output_model, result.content)

    usage = result.usage
    warning = " [경고: 지연 초과]" if elapsed >= LATENCY_WARNING_SECONDS else ""
    print(
        f"\n[{case_name}] {route.provider_name}:{route.model_id} "
        f"지연 {elapsed:.2f}s, 입력 {usage.input_tokens} / 출력 {usage.output_tokens} 토큰{warning}"
    )

    assert output is not None
    assert elapsed < LATENCY_WARNING_SECONDS * 2, (
        f"{case_name} 지연 {elapsed:.2f}s가 공급자 타임아웃에 근접합니다."
    )
