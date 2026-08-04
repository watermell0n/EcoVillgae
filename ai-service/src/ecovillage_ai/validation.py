from __future__ import annotations

import json
import re
from collections.abc import Iterable, Mapping
from enum import StrEnum

from pydantic import BaseModel, ValidationError

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.features.balancing import (
    eligible_evidence_codes,
    validate_balancing_input,
)
from ecovillage_ai.features.economic_event import (
    event_approval_boundary_codes,
    event_content_safety_codes,
    event_grade_and_effect_codes,
    validate_economic_event_input,
)
from ecovillage_ai.features.quiz_draft import (
    quiz_content_safety_codes,
    quiz_grade_quality_codes,
    quiz_structure_quality_codes,
    validate_quiz_draft_input,
)
from ecovillage_ai.features.setup_precheck import (
    highest_preflight_severity,
    preflight_guidance_quality_codes,
    validate_setup_precheck_input,
)
from ecovillage_ai.models import (
    BalancingGenerationRequest,
    BalancingOutput,
    BalancingPayload,
    EconomicEventGenerationRequest,
    EconomicEventOutput,
    ErrorCode,
    GenerationOutput,
    GenerationRequest,
    QuizDraftGenerationRequest,
    QuizDraftOutput,
    SetupPrecheckOutput,
    SetupPrecheckPayload,
    TeacherChatGenerationRequest,
    TeacherChatOutput,
)

UUID_PATTERN = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-"
    r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b"
)
EMAIL_PATTERN = re.compile(
    r"(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
    r"(?![A-Za-z0-9.-])"
)
PHONE_PATTERN = re.compile(r"(?<!\d)01[016789][ -]?\d{3,4}[ -]?\d{4}(?!\d)")
TOKEN_PATTERN = re.compile(
    r"(?i)\b(?:bearer\s+[A-Za-z0-9._~+/=-]+|access[_ -]?token|refresh[_ -]?token|password)\b"
)
PERSONAL_ANALYSIS_PHRASES = ("학생별", "개별 학생", "개인 잔액", "개인 거래")
FINANCIAL_ADVICE_PHRASES = ("수익을 보장", "투자하세요", "대출 받으세요")
FUTURE_PRICE_PHRASES = ("가격이 오를", "가격이 내릴", "미래 가격", "다음 단계 가격")
APPROVAL_BOUNDARY_PHRASES = ("승인 없이", "자동 적용", "즉시 적용", "게시 완료")


class ValidationStage(StrEnum):
    SCHEMA = "SCHEMA"
    CONTEXT = "CONTEXT"
    EVIDENCE = "EVIDENCE"
    RANGE = "RANGE"
    GRADE_AND_EFFECT = "GRADE_AND_EFFECT"
    CONTENT_SAFETY = "CONTENT_SAFETY"
    APPROVAL_BOUNDARY = "APPROVAL_BOUNDARY"


def validate_input_policy(request: GenerationRequest) -> None:
    if isinstance(request, BalancingGenerationRequest):
        if isinstance(request.payload, SetupPrecheckPayload):
            validate_setup_precheck_input(request.payload)
            return
        validate_balancing_input(request)
    elif isinstance(request, EconomicEventGenerationRequest):
        validate_economic_event_input(request)
    elif isinstance(request, QuizDraftGenerationRequest):
        validate_quiz_draft_input(request)


class OutputValidationPipeline:
    """결정적 단계 순서로 공급자 출력을 파싱하고 검증한다."""

    stages = tuple(ValidationStage)

    def validate(
        self,
        request: GenerationRequest,
        output_model: type[GenerationOutput],
        raw_output: object,
    ) -> GenerationOutput:
        output = self._parse_schema(output_model, raw_output)
        self.validate_parsed(request, output)
        return output

    def validate_parsed(
        self, request: GenerationRequest, output: GenerationOutput
    ) -> None:
        validators = (
            (ValidationStage.CONTEXT, _validate_context),
            (ValidationStage.EVIDENCE, _validate_evidence),
            (ValidationStage.RANGE, _validate_range),
            (ValidationStage.GRADE_AND_EFFECT, _validate_grade_and_effect),
            (ValidationStage.CONTENT_SAFETY, _validate_content_safety),
            (ValidationStage.APPROVAL_BOUNDARY, _validate_approval_boundary),
        )
        for stage, validator in validators:
            codes = _unique(validator(request, output))
            if codes:
                _raise_validation(stage, codes)

    @staticmethod
    def _parse_schema(
        output_model: type[GenerationOutput], raw_output: object
    ) -> GenerationOutput:
        if isinstance(raw_output, str):
            try:
                decoded = json.loads(raw_output)
            except json.JSONDecodeError as error:
                raise ServiceError(
                    status_code=422,
                    code=ErrorCode.OUTPUT_SCHEMA_INVALID,
                    message="AI 출력이 유효한 JSON이 아닙니다.",
                    validation_codes=["OUTPUT_JSON_INVALID"],
                    validation_stage=ValidationStage.SCHEMA,
                ) from error
        elif isinstance(raw_output, Mapping):
            decoded = dict(raw_output)
        else:
            raise ServiceError(
                status_code=422,
                code=ErrorCode.OUTPUT_SCHEMA_INVALID,
                message="AI 출력 형식을 해석할 수 없습니다.",
                validation_codes=["OUTPUT_CONTENT_TYPE_INVALID"],
                validation_stage=ValidationStage.SCHEMA,
            )
        if not isinstance(decoded, dict):
            raise ServiceError(
                status_code=422,
                code=ErrorCode.OUTPUT_SCHEMA_INVALID,
                message="AI 출력의 최상위 값은 JSON object여야 합니다.",
                validation_codes=["OUTPUT_JSON_OBJECT_REQUIRED"],
                validation_stage=ValidationStage.SCHEMA,
            )
        try:
            return output_model.model_validate(decoded)
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
                validation_stage=ValidationStage.SCHEMA,
            ) from error


def validate_output_policy(request: GenerationRequest, output: GenerationOutput) -> None:
    OutputValidationPipeline().validate_parsed(request, output)


def _validate_context(
    request: GenerationRequest, output: GenerationOutput
) -> list[str]:
    if isinstance(request, BalancingGenerationRequest):
        if isinstance(request.payload, SetupPrecheckPayload) and isinstance(
            output, SetupPrecheckOutput
        ):
            return _validate_setup_context(request.payload, output)
        if isinstance(output, BalancingOutput) and not isinstance(
            request.payload, SetupPrecheckPayload
        ):
            return _validate_balancing_context(request, request.payload, output)
        return ["OUTPUT_TYPE_MISMATCH"]
    if isinstance(request, TeacherChatGenerationRequest) and isinstance(
        output, TeacherChatOutput
    ):
        codes = []
        if output.period != request.payload.period:
            codes.append("PERIOD_CONTEXT_MISMATCH")
        if output.data_as_of != request.payload.period.data_as_of:
            codes.append("DATA_AS_OF_CONTEXT_MISMATCH")
        return codes
    if isinstance(request, EconomicEventGenerationRequest) and isinstance(
        output, EconomicEventOutput
    ):
        return _validate_event_context(request, output)
    if isinstance(request, QuizDraftGenerationRequest) and isinstance(
        output, QuizDraftOutput
    ):
        codes = []
        if output.category is not request.payload.category:
            codes.append("QUIZ_CATEGORY_MISMATCH")
        if len(output.questions) != request.payload.count:
            codes.append("QUIZ_COUNT_MISMATCH")
        if any(
            question.question_type is not request.payload.question_type
            for question in output.questions
        ):
            codes.append("QUIZ_TYPE_MISMATCH")
        if any(
            question.difficulty is not request.payload.difficulty
            for question in output.questions
        ):
            codes.append("QUIZ_DIFFICULTY_MISMATCH")
        codes.extend(quiz_structure_quality_codes(output))
        return codes
    return ["OUTPUT_TYPE_MISMATCH"]


def _validate_setup_context(
    payload: SetupPrecheckPayload, output: SetupPrecheckOutput
) -> list[str]:
    codes: list[str] = []
    if output.context_type is not payload.context.context_type:
        codes.append("SETUP_CONTEXT_MISMATCH")
    if output.overall_severity is not highest_preflight_severity(payload):
        codes.append("PREFLIGHT_OVERALL_SEVERITY_CHANGED")
    candidate_ids = {candidate.action_id for candidate in payload.candidates}
    explanation_ids = [item.action_id for item in output.candidate_explanations]
    if len(explanation_ids) != len(set(explanation_ids)):
        codes.append("DUPLICATE_ACTION_ID")
    if set(explanation_ids) != candidate_ids:
        codes.append("SETUP_CANDIDATE_MISMATCH")
    check_ids = {check.check_id for check in payload.checks}
    explanation_check_ids = [item.check_id for item in output.check_explanations]
    if len(explanation_check_ids) != len(set(explanation_check_ids)):
        codes.append("DUPLICATE_CHECK_ID")
    if set(explanation_check_ids) != check_ids:
        codes.append("PREFLIGHT_CHECK_MISMATCH")
    return codes


def _validate_balancing_context(
    request: BalancingGenerationRequest,
    payload: BalancingPayload,
    output: BalancingOutput,
) -> list[str]:
    codes: list[str] = []
    if output.analysis_mode is not request.analysis_mode:
        codes.append("ANALYSIS_MODE_CHANGED")
    if output.trigger_type is not request.trigger_type:
        codes.append("ANALYSIS_TRIGGER_CHANGED")
    if output.analysis_severity is not payload.analysis_severity:
        codes.append("ANALYSIS_SEVERITY_CHANGED")
    if output.metric_version != payload.metric_version:
        codes.append("METRIC_VERSION_CHANGED")
    if output.threshold_version != payload.threshold_version:
        codes.append("THRESHOLD_VERSION_CHANGED")
    if output.period != payload.period:
        codes.append("ANALYSIS_PERIOD_CHANGED")
    if output.previous_period != payload.previous_period:
        codes.append("PREVIOUS_PERIOD_CHANGED")

    proposal_ids = [proposal.action_id for proposal in output.proposals]
    if len(proposal_ids) != len(set(proposal_ids)):
        codes.append("DUPLICATE_ACTION_ID")
    priorities = [proposal.priority for proposal in output.proposals]
    if priorities != list(range(1, len(priorities) + 1)):
        codes.append("PROPOSAL_PRIORITY_INVALID")
    if payload.analysis_severity.value == "NORMAL" and output.proposals:
        codes.append("NORMAL_PROPOSAL_FORBIDDEN")
    return codes


def _validate_event_context(
    request: EconomicEventGenerationRequest, output: EconomicEventOutput
) -> list[str]:
    codes: list[str] = []
    if output.template_type is not request.payload.template_type:
        codes.append("EVENT_TEMPLATE_MISMATCH")
    if output.source_type is not request.payload.source_type:
        codes.append("EVENT_SOURCE_CHANGED")
    if output.source_proposal_id != request.payload.source_proposal_id:
        codes.append("EVENT_SOURCE_PROPOSAL_CHANGED")
    if output.source_evidence_codes != request.payload.source_evidence_codes:
        codes.append("EVENT_SOURCE_EVIDENCE_CHANGED")
    if output.interest_rate_scope is not request.payload.interest_rate_scope:
        codes.append("EVENT_INTEREST_RATE_SCOPE_CHANGED")
    input_context = request.payload.investment_news_context
    output_binding = output.investment_news_binding
    if input_context is not None and output_binding is not None:
        if (
            output_binding.scenario_version_id != input_context.scenario_version_id
            or output_binding.asset_id != input_context.asset_id
            or output_binding.price_step_id != input_context.price_step_id
            or output_binding.target_price_step_id != input_context.target_price_step_id
            or output_binding.asset_name != input_context.asset_name
            or output_binding.asset_symbol != input_context.asset_symbol
            or output_binding.investment_mode is not input_context.investment_mode
            or output_binding.scenario_source is not input_context.scenario_source
            or output_binding.settlement_rule is not input_context.settlement_rule
            or output_binding.current_step_index != input_context.current_step_index
            or output_binding.target_step_index != input_context.target_step_index
        ):
            codes.append("INVESTMENT_NEWS_BINDING_MISMATCH")
    return codes


def _validate_evidence(
    request: GenerationRequest, output: GenerationOutput
) -> list[str]:
    codes: list[str] = []
    if isinstance(request, BalancingGenerationRequest):
        if isinstance(request.payload, SetupPrecheckPayload) and isinstance(
            output, SetupPrecheckOutput
        ):
            candidate_by_id = {
                candidate.action_id: candidate for candidate in request.payload.candidates
            }
            check_by_id = {check.check_id: check for check in request.payload.checks}
            for explanation in output.candidate_explanations:
                candidate = candidate_by_id.get(explanation.action_id)
                if candidate is not None:
                    output_reasons = set(explanation.evidence_reason_codes)
                    input_reasons = set(candidate.reason_codes)
                    if not output_reasons <= input_reasons:
                        codes.append("FABRICATED_EVIDENCE")
                    elif explanation.evidence_reason_codes != candidate.reason_codes:
                        codes.append("SETUP_REASON_CODES_CHANGED")
            for explanation in output.check_explanations:
                check = check_by_id.get(explanation.check_id)
                if check is not None and explanation.reason_code != check.reason_code:
                    codes.append("PREFLIGHT_REASON_CHANGED")
                if (
                    check is not None
                    and explanation.subject_action_ids != check.subject_action_ids
                ):
                    codes.append("PREFLIGHT_SUBJECTS_CHANGED")
        elif isinstance(output, BalancingOutput) and not isinstance(
            request.payload, SetupPrecheckPayload
        ):
            metric_codes = eligible_evidence_codes(request)
            has_fabricated_evidence = not set(output.evidence_metric_codes) <= metric_codes
            if has_fabricated_evidence:
                codes.append("FABRICATED_EVIDENCE")
            referenced_codes: set[str] = set()
            for cause in output.causes:
                referenced_codes.update(cause.evidence_metric_codes)
                if not set(cause.evidence_metric_codes) <= metric_codes:
                    has_fabricated_evidence = True
                    codes.append("FABRICATED_EVIDENCE")
            for proposal in output.proposals:
                referenced_codes.update(proposal.evidence_metric_codes)
                if not set(proposal.evidence_metric_codes) <= metric_codes:
                    has_fabricated_evidence = True
                    codes.append("FABRICATED_EVIDENCE")
            if (
                not has_fabricated_evidence
                and set(output.evidence_metric_codes) != referenced_codes
            ):
                codes.append("EVIDENCE_SUMMARY_MISMATCH")
    elif isinstance(request, TeacherChatGenerationRequest) and isinstance(
        output, TeacherChatOutput
    ):
        fact_ids = {fact.fact_id for fact in request.payload.facts}
        fact_ids.update(
            change.fact_id for change in request.payload.recent_policy_changes
        )
        output_fact_ids = set(output.evidence_fact_ids)
        output_fact_ids.update(
            fact_id
            for item in (*output.causes, *output.recommendations)
            for fact_id in item.evidence_fact_ids
        )
        if not output_fact_ids <= fact_ids:
            codes.append("FABRICATED_EVIDENCE")
    elif isinstance(request, QuizDraftGenerationRequest) and isinstance(
        output, QuizDraftOutput
    ):
        allowed_concepts = set(request.payload.concepts)
        if any(
            not set(question.concept_tags) <= allowed_concepts
            for question in output.questions
        ):
            codes.append("FABRICATED_CONCEPT_TAG")
    return codes


def _validate_range(request: GenerationRequest, output: GenerationOutput) -> list[str]:
    codes: list[str] = []
    if not isinstance(request, BalancingGenerationRequest):
        return codes
    if isinstance(request.payload, SetupPrecheckPayload) and isinstance(
        output, SetupPrecheckOutput
    ):
        candidate_by_id = {
            candidate.action_id: candidate for candidate in request.payload.candidates
        }
        check_by_id = {check.check_id: check for check in request.payload.checks}
        for explanation in output.candidate_explanations:
            candidate = candidate_by_id.get(explanation.action_id)
            if candidate is not None and explanation.proposed_value != candidate.proposed_value:
                codes.append("PROPOSED_VALUE_CHANGED")
            if candidate is not None and (
                explanation.recommended_min_value != candidate.recommended_min_value
                or explanation.recommended_max_value != candidate.recommended_max_value
                or explanation.allowed_min_value != candidate.allowed_min_value
                or explanation.allowed_max_value != candidate.allowed_max_value
            ):
                codes.append("SETUP_RANGE_CHANGED")
            if candidate is not None and explanation.unit is not candidate.unit:
                codes.append("SETUP_UNIT_CHANGED")
            if candidate is not None and explanation.target_type is not candidate.target_type:
                codes.append("SETUP_TARGET_TYPE_CHANGED")
            if candidate is not None and explanation.label != candidate.label:
                codes.append("SETUP_LABEL_CHANGED")
        for explanation in output.check_explanations:
            check = check_by_id.get(explanation.check_id)
            if check is not None and explanation.severity is not check.severity:
                codes.append("PREFLIGHT_SEVERITY_CHANGED")
    elif isinstance(output, BalancingOutput) and not isinstance(
        request.payload, SetupPrecheckPayload
    ):
        action_by_id = {
            action.action_id: action for action in request.payload.allowed_actions
        }
        for proposal in output.proposals:
            action = action_by_id.get(proposal.action_id)
            if action is None:
                codes.append("UNKNOWN_ACTION_ID")
                continue
            if proposal.target_type is not action.target_type:
                codes.append("ACTION_TARGET_TYPE_CHANGED")
            if proposal.label != action.label:
                codes.append("ACTION_LABEL_CHANGED")
            if proposal.current_value != action.current_value:
                codes.append("ACTION_CURRENT_VALUE_CHANGED")
            if (
                proposal.min_value != action.min_value
                or proposal.max_value != action.max_value
            ):
                codes.append("ACTION_RANGE_CHANGED")
            if proposal.unit is not action.unit:
                codes.append("ACTION_UNIT_CHANGED")
            if not action.min_value <= proposal.proposed_value <= action.max_value:
                codes.append("PROPOSED_VALUE_OUT_OF_RANGE")
    return codes


def _validate_grade_and_effect(
    request: GenerationRequest, output: GenerationOutput
) -> list[str]:
    if (
        isinstance(request, BalancingGenerationRequest)
        and isinstance(request.payload, SetupPrecheckPayload)
        and isinstance(output, SetupPrecheckOutput)
    ):
        return preflight_guidance_quality_codes(request.payload, output)
    if isinstance(request, QuizDraftGenerationRequest) and isinstance(
        output, QuizDraftOutput
    ):
        return quiz_grade_quality_codes(request, output)
    if not isinstance(request, EconomicEventGenerationRequest) or not isinstance(
        output, EconomicEventOutput
    ):
        return []
    return event_grade_and_effect_codes(request, output)


def _validate_content_safety(
    request: GenerationRequest, output: GenerationOutput
) -> list[str]:
    text = " ".join(_iter_natural_language(output))
    codes: list[str] = []
    if UUID_PATTERN.search(text) or EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text):
        codes.append("SENSITIVE_DATA_DETECTED")
    if TOKEN_PATTERN.search(text):
        codes.append("SECRET_PATTERN_DETECTED")
    if any(phrase in text for phrase in PERSONAL_ANALYSIS_PHRASES):
        codes.append("PERSONAL_ANALYSIS_DETECTED")
    if any(phrase in text for phrase in FINANCIAL_ADVICE_PHRASES):
        codes.append("FINANCIAL_ADVICE_DETECTED")
    if any(phrase in text for phrase in FUTURE_PRICE_PHRASES):
        codes.append("FUTURE_PRICE_PREDICTION_DETECTED")
    if isinstance(output, QuizDraftOutput):
        codes.extend(quiz_content_safety_codes(output))
    if isinstance(request, EconomicEventGenerationRequest) and isinstance(
        output, EconomicEventOutput
    ):
        codes.extend(event_content_safety_codes(output))
    return codes


def _validate_approval_boundary(
    request: GenerationRequest, output: GenerationOutput
) -> list[str]:
    text = " ".join(_iter_natural_language(output))
    codes = []
    if any(phrase in text for phrase in APPROVAL_BOUNDARY_PHRASES):
        codes.append("APPROVAL_BOUNDARY_VIOLATION")
    if isinstance(request, EconomicEventGenerationRequest) and isinstance(
        output, EconomicEventOutput
    ):
        codes.extend(event_approval_boundary_codes(output))
    return codes


def _raise_validation(stage: ValidationStage, codes: list[str]) -> None:
    safety_stages = {
        ValidationStage.GRADE_AND_EFFECT,
        ValidationStage.CONTENT_SAFETY,
        ValidationStage.APPROVAL_BOUNDARY,
    }
    if stage in safety_stages:
        code = ErrorCode.OUTPUT_SAFETY_REJECTED
        message = "AI 출력이 안전 경계를 충족하지 않습니다."
    else:
        code = ErrorCode.OUTPUT_SCHEMA_INVALID
        message = "AI 출력이 구조화 응답 계약을 충족하지 않습니다."
    raise ServiceError(
        status_code=422,
        code=code,
        message=message,
        validation_codes=codes,
        validation_stage=stage,
    )


def _iter_strings(value: object) -> Iterable[str]:
    if isinstance(value, BaseModel):
        yield from _iter_strings(value.model_dump(mode="python", by_alias=True))
    elif isinstance(value, dict):
        for nested in value.values():
            yield from _iter_strings(nested)
    elif isinstance(value, (list, tuple, set)):
        for nested in value:
            yield from _iter_strings(nested)
    elif isinstance(value, str):
        yield value


def _iter_natural_language(output: GenerationOutput) -> Iterable[str]:
    if isinstance(output, TeacherChatOutput):
        yield output.answer
        for cause in output.causes:
            yield cause.text
        for recommendation in output.recommendations:
            yield recommendation.text
            yield from recommendation.side_effects
        return
    yield from _iter_strings(output)


def _unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))
