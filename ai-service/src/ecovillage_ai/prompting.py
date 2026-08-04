from __future__ import annotations

import json
from dataclasses import dataclass
from importlib.resources import files
from typing import Any

from pydantic import BaseModel

from ecovillage_ai.models import (
    AnalysisMode,
    BalancingGenerationRequest,
    EconomicEventGenerationRequest,
    GenerationRequest,
    InvestmentMode,
    QuizDraftGenerationRequest,
    TeacherChatGenerationRequest,
)
from ecovillage_ai.runtime import GenerationCommand, GenerationKind

GRADE_INSTRUCTIONS = {
    4: "초등학교 4학년이 이해할 수 있는 짧고 구체적인 표현을 사용한다.",
    5: "초등학교 5학년 수준의 경제 개념을 일상 사례와 연결해 설명한다.",
    6: "초등학교 6학년 수준에서 원인과 영향을 구분하되 실제 금융 조언은 하지 않는다.",
}
SUPPORTED_PROMPT_VERSIONS = (
    "setup-precheck-v1",
    "balancing-v1",
    "teacher-chat-v1",
    "economic-event-v1",
    "quiz-draft-v1",
)


class PromptRegistryError(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.safe_message = message


@dataclass(frozen=True, slots=True)
class PromptSelection:
    kind: GenerationKind
    mode: str
    grade: int
    prompt_version: str
    asset_path: str

    @property
    def key(self) -> str:
        return f"{self.kind.value}.{self.mode}.grade-{self.grade}.{self.prompt_version}"


class PromptRegistry:
    """승인된 정적 지침과 최소 신뢰 입력을 공급자 명령으로 조립한다."""

    def build_command(
        self,
        kind: GenerationKind,
        request: GenerationRequest,
        output_model: type[BaseModel],
    ) -> GenerationCommand:
        selection = self.select(kind, request)
        system_instruction = "\n\n".join(
            (
                self._read_asset("v1/common.txt"),
                self._read_asset(selection.asset_path),
                GRADE_INSTRUCTIONS[selection.grade],
            )
        )
        trusted_input = self._trusted_input(request)
        trusted_input_json = json.dumps(
            trusted_input,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        return GenerationCommand(
            kind=kind,
            prompt_key=selection.key,
            prompt_version=selection.prompt_version,
            system_instruction=system_instruction,
            trusted_input_json=trusted_input_json,
            output_schema=output_model.model_json_schema(by_alias=True),
        )

    def select(
        self, kind: GenerationKind, request: GenerationRequest
    ) -> PromptSelection:
        mode, expected_version, asset_path = self._selection_parts(kind, request)
        if request.prompt_version != expected_version:
            raise PromptRegistryError(
                "PROMPT_VERSION_MISMATCH",
                "요청한 프롬프트 버전이 기능과 실행 모드에 맞지 않습니다.",
            )
        return PromptSelection(
            kind=kind,
            mode=mode,
            grade=request.grade,
            prompt_version=expected_version,
            asset_path=asset_path,
        )

    @staticmethod
    def _selection_parts(
        kind: GenerationKind, request: GenerationRequest
    ) -> tuple[str, str, str]:
        if kind is GenerationKind.BALANCING and isinstance(
            request, BalancingGenerationRequest
        ):
            selections = {
                AnalysisMode.SETUP_PRECHECK: (
                    "setup-precheck",
                    "setup-precheck-v1",
                    "v1/balancing/setup-precheck.txt",
                ),
                AnalysisMode.ON_DEMAND: (
                    "on-demand",
                    "balancing-v1",
                    "v1/balancing/on-demand.txt",
                ),
                AnalysisMode.AUTO_MONITOR: (
                    "auto-monitor",
                    "balancing-v1",
                    "v1/balancing/auto-monitor.txt",
                ),
            }
            return selections[request.analysis_mode]
        if kind is GenerationKind.TEACHER_CHAT and isinstance(
            request, TeacherChatGenerationRequest
        ):
            return "answer", "teacher-chat-v1", "v1/teacher-chat.txt"
        if kind is GenerationKind.ECONOMIC_EVENT and isinstance(
            request, EconomicEventGenerationRequest
        ):
            if (
                request.payload.template_type.value == "INVESTMENT_NEWS"
                and request.payload.investment_news_context is not None
            ):
                investment_mode = request.payload.investment_news_context.investment_mode
                mode = (
                    "investment-news-short-term"
                    if investment_mode is InvestmentMode.SHORT_TERM
                    else "investment-news-long-term"
                )
                return mode, "economic-event-v1", f"v1/economic-event/{mode}.txt"
            mode = request.payload.template_type.value.casefold().replace("_", "-")
            return mode, "economic-event-v1", f"v1/economic-event/{mode}.txt"
        if kind is GenerationKind.QUIZ_DRAFT and isinstance(
            request, QuizDraftGenerationRequest
        ):
            category = request.payload.category.value.casefold()
            question_type = request.payload.question_type.value.casefold().replace("_", "-")
            return (
                f"{category}-{question_type}",
                "quiz-draft-v1",
                "v1/quiz-draft.txt",
            )
        raise PromptRegistryError(
            "PROMPT_COMBINATION_UNSUPPORTED",
            "생성 기능과 요청 문맥에 맞는 프롬프트가 등록되어 있지 않습니다.",
        )

    @staticmethod
    def _trusted_input(request: GenerationRequest) -> dict[str, Any]:
        trusted: dict[str, Any] = {
            "grade": request.grade,
            "locale": request.locale,
            "payload": request.payload.model_dump(mode="json", by_alias=True),
        }
        if isinstance(request, BalancingGenerationRequest):
            trusted["analysis_mode"] = request.analysis_mode.value
            trusted["trigger_type"] = request.trigger_type.value
        return trusted

    @staticmethod
    def _read_asset(relative_path: str) -> str:
        resource = files("ecovillage_ai.prompts").joinpath(relative_path)
        try:
            content = resource.read_text(encoding="utf-8").strip()
        except (FileNotFoundError, OSError) as error:
            raise PromptRegistryError(
                "PROMPT_ASSET_MISSING",
                "승인된 프롬프트 파일을 찾을 수 없습니다.",
            ) from error
        if not content:
            raise PromptRegistryError(
                "PROMPT_ASSET_EMPTY",
                "승인된 프롬프트 파일이 비어 있습니다.",
            )
        return content
