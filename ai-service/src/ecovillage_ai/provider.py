from __future__ import annotations

import json
from typing import Any, Protocol

from ecovillage_ai.config import Settings
from ecovillage_ai.models import Usage
from ecovillage_ai.runtime import GenerationCommand, GenerationKind, ProviderResult


class GenerationProvider(Protocol):
    async def generate(self, command: GenerationCommand) -> ProviderResult: ...


class FakeGenerationProvider:
    """외부 호출 없이 공통 생성 명령으로 결정적 JSON 결과를 만든다."""

    provider_name = "FAKE"

    def __init__(self, model_id: str = "deterministic-v1") -> None:
        self._model_id = model_id

    def provider_name_for(self, kind: GenerationKind) -> str:
        return self.provider_name

    def model_id_for(self, kind: GenerationKind) -> str:
        return self._model_id

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        trusted_input = json.loads(command.trusted_input_json)
        output = self._generate_output(command.kind, trusted_input)
        content = json.dumps(output, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        input_size = (
            len(command.system_instruction)
            + len(command.trusted_input_json)
            + len(json.dumps(command.output_schema, ensure_ascii=False, sort_keys=True))
        )
        return ProviderResult(
            content=content,
            usage=Usage(
                input_tokens=max(1, input_size // 4),
                output_tokens=max(1, len(content) // 4),
            ),
        )

    def _generate_output(
        self, kind: GenerationKind, trusted_input: dict[str, Any]
    ) -> dict[str, Any]:
        if kind is GenerationKind.BALANCING:
            return self._balancing(trusted_input)
        if kind is GenerationKind.TEACHER_CHAT:
            return self._teacher_chat(trusted_input["payload"])
        if kind is GenerationKind.ECONOMIC_EVENT:
            return self._economic_event(trusted_input["payload"])
        if kind is GenerationKind.QUIZ_DRAFT:
            return self._quiz_draft(trusted_input["payload"])
        raise TypeError("지원하지 않는 생성 종류입니다.")

    @staticmethod
    def _balancing(trusted_input: dict[str, Any]) -> dict[str, Any]:
        payload = trusted_input["payload"]
        if trusted_input["analysis_mode"] == "SETUP_PRECHECK":
            return FakeGenerationProvider._setup_precheck(payload)

        metric = next(
            metric for metric in payload["metrics"] if metric["availability"] == "AVAILABLE"
        )
        proposals = []
        if payload["allowed_actions"]:
            action = payload["allowed_actions"][0]
            proposals.append(
                {
                    "action_id": action["action_id"],
                    "proposed_value": action["current_value"],
                    "evidence_metric_codes": [metric["code"]],
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
            "evidence_metric_codes": [metric["code"]],
            "causes": ["현재 기간의 집계 지표 변화가 관찰되었습니다."],
            "proposals": proposals,
        }

    @staticmethod
    def _setup_precheck(payload: dict[str, Any]) -> dict[str, Any]:
        return {
            "review_status": "REVIEW_REQUIRED",
            "context_type": payload["context"]["context_type"],
            "summary": "규칙 엔진이 계산한 설정 후보와 시작 전 점검 결과를 설명했습니다.",
            "candidate_explanations": [
                {
                    "action_id": candidate["action_id"],
                    "proposed_value": candidate["proposed_value"],
                    "evidence_reason_codes": candidate["reason_codes"],
                    "rationale": "현재 수업 설정에 맞춰 계산된 후보값의 의미를 확인해 보세요.",
                    "cautions": ["실제 적용 전 현재 수업 계획과 함께 검토해야 합니다."],
                }
                for candidate in payload["candidates"]
            ],
            "check_explanations": [
                {
                    "check_id": check["check_id"],
                    "severity": check["severity"],
                    "reason_code": check["reason_code"],
                    "explanation": check["message"],
                    "correction_guidance": (
                        None
                        if check["severity"] == "PASS"
                        else "관련 설정 후보와 허용 범위를 다시 확인해 주세요."
                    ),
                }
                for check in payload["checks"]
            ],
        }

    @staticmethod
    def _teacher_chat(payload: dict[str, Any]) -> dict[str, Any]:
        facts = payload["facts"][:2]
        fact_ids = [fact["fact_id"] for fact in facts]
        policy_changes = payload["recent_policy_changes"][:1]
        fact_ids.extend(change["fact_id"] for change in policy_changes)
        fact_summary = ", ".join(
            f"{fact['label']}={fact['value']}" for fact in facts
        )
        if policy_changes:
            fact_summary += f", 최근 정책 변화={policy_changes[0]['summary']}"
        return {
            "response_mode": "ANSWER",
            "answer": f"제공된 학급 집계 사실을 보면 {fact_summary}입니다.",
            "evidence_fact_ids": fact_ids,
            "causes": [
                {
                    "text": "현재 조회 기간에 관찰된 집계 사실을 함께 살펴볼 필요가 있습니다.",
                    "evidence_fact_ids": fact_ids,
                }
            ],
            "recommendations": [
                {
                    "text": "같은 기간의 다른 집계 지표와 함께 비교해 원인을 확인해 보세요.",
                    "evidence_fact_ids": fact_ids,
                    "side_effects": ["단일 지표만 보면 일시적 변화를 과대 해석할 수 있습니다."],
                }
            ],
            "limitation_codes": [],
            "period": payload["period"],
            "data_as_of": payload["period"]["data_as_of"],
        }

    @staticmethod
    def _economic_event(payload: dict[str, Any]) -> dict[str, Any]:
        titles = {
            "PRICE": "마을 시장의 새로운 소식",
            "SUPPLY": "마을 창고에 생긴 변화",
            "TAX": "마을 회의에서 온 안내",
            "INTEREST_RATE": "은행에서 전한 새로운 안내",
            "INVESTMENT_NEWS": "가상 자산 시장의 새로운 소식",
            "REWARD": "활동 보상에 관한 소식",
            "PUBLIC_FUND": "공공기금 사용을 위한 회의",
        }
        template = payload["template_type"]
        output: dict[str, Any] = {
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
            "investment_news_binding": None,
            "teacher_explanation": None,
        }
        if template == "INVESTMENT_NEWS":
            context = payload["investment_news_context"]
            output["investment_news_binding"] = {
                "scenario_version_id": context["scenario_version_id"],
                "asset_id": context["asset_id"],
                "price_step_id": context["price_step_id"],
                "current_step_index": context["current_step_index"],
            }
            output["teacher_explanation"] = (
                "입력으로 받은 현재 공개 단계의 사실만 사용한 검토용 뉴스입니다."
            )
        return output

    @staticmethod
    def _quiz_draft(payload: dict[str, Any]) -> dict[str, Any]:
        questions = []
        for index in range(payload["count"]):
            if payload["question_type"] == "OX":
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
                f"{payload['topic']}에 대해 올바르게 생각한 내용을 고르세요. 문항 {index + 1}"
            )
            questions.append(
                {
                    "review_status": "REVIEW_REQUIRED",
                    "question_type": payload["question_type"],
                    "question": question_text,
                    "choices": choices,
                    "correct_choice_key": correct_key,
                    "explanation": "경제 선택은 필요한 정보와 그 영향을 함께 살펴보아야 합니다.",
                    "difficulty": payload["difficulty"],
                    "concept_tags": [payload["concepts"][index % len(payload["concepts"])]],
                }
            )
        return {
            "review_status": "REVIEW_REQUIRED",
            "category": payload["category"],
            "questions": questions,
        }


def build_generation_provider(settings: Settings) -> GenerationProvider:
    from ecovillage_ai.providers import (
        BedrockGenerationProvider,
        OpenAIGenerationProvider,
        RoutedGenerationProvider,
    )

    settings.validate_provider_environment()
    providers: dict[tuple[str, str], GenerationProvider] = {}
    routes: dict[GenerationKind, GenerationProvider] = {}
    for kind in GenerationKind:
        route = settings.route_for(kind.value)
        key = (route.provider_name, route.model_id)
        provider = providers.get(key)
        if provider is None:
            if route.provider_name == "FAKE":
                provider = FakeGenerationProvider(model_id=route.model_id)
            elif route.provider_name == "BEDROCK":
                if settings.aws_region is None:
                    raise RuntimeError("BEDROCK 사용 시 AWS 리전이 필요합니다.")
                provider = BedrockGenerationProvider(
                    model_id=route.model_id,
                    region_name=settings.aws_region,
                    timeout_seconds=settings.provider_timeout_seconds,
                    max_output_tokens=settings.provider_max_output_tokens,
                )
            else:
                provider = OpenAIGenerationProvider(
                    model_id=route.model_id,
                    timeout_seconds=settings.provider_timeout_seconds,
                    max_output_tokens=settings.provider_max_output_tokens,
                )
            providers[key] = provider
        routes[kind] = provider
    return RoutedGenerationProvider(routes)


__all__ = [
    "FakeGenerationProvider",
    "build_generation_provider",
    "GenerationCommand",
    "GenerationKind",
    "GenerationProvider",
    "ProviderResult",
]
