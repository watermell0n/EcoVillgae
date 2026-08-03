from __future__ import annotations

import os

import pytest

from ecovillage_ai.config import Settings
from ecovillage_ai.models import TeacherChatGenerationRequest, TeacherChatOutput
from ecovillage_ai.prompting import PromptRegistry
from ecovillage_ai.provider import build_generation_provider
from ecovillage_ai.runtime import GenerationKind
from ecovillage_ai.validation import OutputValidationPipeline
from tests.factories import teacher_chat_request

SMOKE_ENABLED = os.getenv("ECOVILLAGE_AI_RUN_PROVIDER_SMOKE", "").lower() in {
    "1",
    "true",
    "yes",
}


@pytest.mark.provider_smoke
@pytest.mark.skipif(
    not SMOKE_ENABLED,
    reason="실제 공급자 smoke는 ECOVILLAGE_AI_RUN_PROVIDER_SMOKE=true에서만 실행합니다.",
)
@pytest.mark.asyncio
async def test_selected_real_provider_follows_teacher_chat_contract() -> None:
    settings = Settings.from_env()
    route = settings.route_for(GenerationKind.TEACHER_CHAT.value)
    assert route.provider_name in {"BEDROCK", "OPENAI"}
    provider = build_generation_provider(settings)
    request = TeacherChatGenerationRequest.model_validate(teacher_chat_request())
    command = PromptRegistry().build_command(
        GenerationKind.TEACHER_CHAT,
        request,
        TeacherChatOutput,
    )

    result = await provider.generate(command)
    output = OutputValidationPipeline().validate(
        request,
        TeacherChatOutput,
        result.content,
    )

    assert output.evidence_fact_ids
