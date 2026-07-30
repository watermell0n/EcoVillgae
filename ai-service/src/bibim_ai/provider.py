from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import Any, Protocol

from bibim_ai.models import (
    BalancingGenerationRequest,
    EconomicEventGenerationRequest,
    GenerationRequest,
    MetricAvailability,
    QuizDraftGenerationRequest,
    QuizQuestionType,
    TeacherChatGenerationRequest,
    Usage,
)


class GenerationKind(StrEnum):
    BALANCING = "balancing"
    TEACHER_CHAT = "teacher-chat"
    ECONOMIC_EVENT = "economic-event"
    QUIZ_DRAFT = "quiz-draft"


@dataclass(frozen=True, slots=True)
class ProviderResult:
    output: dict[str, Any]
    usage: Usage


class GenerationProvider(Protocol):
    async def generate(
        self, kind: GenerationKind, request: GenerationRequest
    ) -> ProviderResult: ...


class FakeGenerationProvider:
    """External calls 없이 계약 검증에 사용할 결정적 생성 결과를 만든다."""

    async def generate(self, kind: GenerationKind, request: GenerationRequest) -> ProviderResult:
        output = self._generate_output(kind, request)
        input_size = len(request.model_dump_json(by_alias=True))
        output_size = len(str(output))
        return ProviderResult(
            output=output,
            usage=Usage(
                input_tokens=max(1, input_size // 4),
                output_tokens=max(1, output_size // 4),
            ),
        )

    def _generate_output(self, kind: GenerationKind, request: GenerationRequest) -> dict[str, Any]:
        if kind is GenerationKind.BALANCING and isinstance(request, BalancingGenerationRequest):
            return self._balancing(request)
        if kind is GenerationKind.TEACHER_CHAT and isinstance(
            request, TeacherChatGenerationRequest
        ):
            return self._teacher_chat(request)
        if kind is GenerationKind.ECONOMIC_EVENT and isinstance(
            request, EconomicEventGenerationRequest
        ):
            return self._economic_event(request)
        if kind is GenerationKind.QUIZ_DRAFT and isinstance(request, QuizDraftGenerationRequest):
            return self._quiz_draft(request)
        raise TypeError("생성 종류와 요청 모델이 일치하지 않습니다.")

    @staticmethod
    def _balancing(request: BalancingGenerationRequest) -> dict[str, Any]:
        metric = next(
            metric
            for metric in request.payload.metrics
            if metric.availability is MetricAvailability.AVAILABLE
        )
        proposals = []
        if request.payload.allowed_actions:
            action = request.payload.allowed_actions[0]
            proposals.append(
                {
                    "action_id": action.action_id,
                    "proposed_value": action.current_value,
                    "evidence_metric_codes": [metric.code.value],
                    "rationale": "현재 집계 지표를 기준으로 허용 범위 안의 값을 유지합니다.",
                    "expected_effect": "급격한 정책 변경 없이 학급 경제 흐름을 관찰할 수 있습니다.",
                    "side_effects": [
                        "변화가 작아 효과 확인에 추가 관찰 기간이 필요할 수 있습니다."
                    ],
                    "priority": 1,
                }
            )
        return {
            "summary": "학급 집계 지표와 허용된 조정 범위를 바탕으로 검토용 제안을 만들었습니다.",
            "evidence_metric_codes": [metric.code.value],
            "causes": ["현재 기간의 집계 지표 변화가 관찰되었습니다."],
            "proposals": proposals,
        }

    @staticmethod
    def _teacher_chat(request: TeacherChatGenerationRequest) -> dict[str, Any]:
        fact = request.payload.facts[0]
        return {
            "answer": f"제공된 학급 집계 사실 중 '{fact.label}' 항목을 우선 확인해 보세요.",
            "evidence_fact_ids": [fact.fact_id],
            "recommendations": [
                {
                    "text": "같은 기간의 다른 집계 지표와 함께 비교해 원인을 확인해 보세요.",
                    "side_effects": ["단일 지표만 보면 일시적 변화를 과대 해석할 수 있습니다."],
                }
            ],
            "period": request.payload.period.model_dump(mode="json", by_alias=True),
            "data_as_of": request.payload.period.data_as_of.isoformat(),
        }

    @staticmethod
    def _economic_event(request: EconomicEventGenerationRequest) -> dict[str, Any]:
        titles = {
            "PRICE": "마을 시장의 새로운 소식",
            "SUPPLY": "마을 창고에 생긴 변화",
            "TAX": "마을 회의에서 온 안내",
            "INTEREST_RATE": "은행에서 전한 새로운 안내",
            "INVESTMENT_NEWS": "마을 기업에서 온 소식",
            "REWARD": "활동 보상에 관한 소식",
            "PUBLIC_FUND": "공공기금 사용을 위한 회의",
        }
        template = request.payload.template_type.value
        return {
            "review_status": "REVIEW_REQUIRED",
            "template_type": template,
            "title": titles[template],
            "narrative": "마을의 경제 환경이 달라져 주민들이 함께 상황을 살펴보고 있습니다.",
            "choices": [
                {
                    "choice_key": "WAIT_AND_SEE",
                    "label": "상황을 더 살펴본다",
                    "consequence_text": "추가 소식을 확인한 뒤 차분하게 판단합니다.",
                },
                {
                    "choice_key": "DISCUSS",
                    "label": "친구들과 의견을 나눈다",
                    "consequence_text": "서로 다른 생각을 비교하며 선택의 영향을 살펴봅니다.",
                },
            ],
            "progress_messages": ["교사 검토를 기다리는 경제 이벤트 초안이 준비되었습니다."],
        }

    @staticmethod
    def _quiz_draft(request: QuizDraftGenerationRequest) -> dict[str, Any]:
        questions = []
        for index in range(request.payload.count):
            if request.payload.question_type is QuizQuestionType.OX:
                choices = [
                    {"choice_key": "O", "text": "맞다"},
                    {"choice_key": "X", "text": "아니다"},
                ]
                correct_key = "O"
            else:
                choices = [
                    {"choice_key": "A", "text": "필요와 선택을 함께 생각한다"},
                    {"choice_key": "B", "text": "가격만 보고 바로 결정한다"},
                    {"choice_key": "C", "text": "다른 정보는 확인하지 않는다"},
                ]
                correct_key = "A"
            question_text = (
                f"{request.payload.topic}에 대해 올바르게 생각한 내용을 고르세요. 문항 {index + 1}"
            )
            questions.append(
                {
                    "review_status": "REVIEW_REQUIRED",
                    "question_type": request.payload.question_type.value,
                    "question": question_text,
                    "choices": choices,
                    "correct_choice_key": correct_key,
                    "explanation": "경제 선택은 필요한 정보와 그 영향을 함께 살펴보아야 합니다.",
                    "age_appropriateness_tags": [f"초{request.grade}", "쉬운표현"],
                }
            )
        return {
            "review_status": "REVIEW_REQUIRED",
            "category": request.payload.category.value,
            "questions": questions,
        }
