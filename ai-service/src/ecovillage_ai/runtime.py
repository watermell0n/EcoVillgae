from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

from ecovillage_ai.models import Usage


class GenerationKind(StrEnum):
    BALANCING = "balancing"
    TEACHER_CHAT = "teacher-chat"
    ECONOMIC_EVENT = "economic-event"
    QUIZ_DRAFT = "quiz-draft"


@dataclass(frozen=True, slots=True)
class GenerationCommand:
    kind: GenerationKind
    prompt_key: str
    prompt_version: str
    system_instruction: str
    trusted_input_json: str
    output_schema: dict[str, Any]

    def __post_init__(self) -> None:
        if not self.prompt_key or not self.prompt_version:
            raise ValueError("생성 명령에는 프롬프트 식별자와 버전이 필요합니다.")
        if not self.system_instruction.strip():
            raise ValueError("생성 명령에는 시스템 지침이 필요합니다.")
        if not self.trusted_input_json.strip():
            raise ValueError("생성 명령에는 신뢰 입력 JSON이 필요합니다.")
        if self.output_schema.get("type") != "object":
            raise ValueError("생성 명령의 출력 스키마는 JSON object여야 합니다.")


ProviderContent = str | Mapping[str, Any]


@dataclass(frozen=True, slots=True)
class ProviderResult:
    content: ProviderContent
    usage: Usage
