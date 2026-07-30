from __future__ import annotations

import re
from collections.abc import Iterable

from pydantic import BaseModel

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.models import (
    AnalysisMode,
    AnalysisSeverity,
    BalancingGenerationRequest,
    BalancingOutput,
    EconomicEventGenerationRequest,
    EconomicEventOutput,
    ErrorCode,
    EventTemplateType,
    GenerationOutput,
    GenerationRequest,
    InterestRateScope,
    MetricAvailability,
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
EMAIL_PATTERN = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
PHONE_PATTERN = re.compile(r"(?<!\d)01[016789][ -]?\d{3,4}[ -]?\d{4}(?!\d)")
TOKEN_PATTERN = re.compile(
    r"(?i)\b(?:bearer\s+[A-Za-z0-9._~+/=-]+|access[_ -]?token|refresh[_ -]?token|password)\b"
)
APPROVAL_BOUNDARY_PHRASES = ("승인 없이", "자동 적용", "즉시 적용", "게시 완료")
FINANCIAL_ADVICE_PHRASES = ("수익을 보장", "투자하세요", "대출 받으세요", "가격이 오를")


def validate_input_policy(request: GenerationRequest) -> None:
    if isinstance(request, BalancingGenerationRequest):
        if isinstance(request.payload, SetupPrecheckPayload):
            return
        if (
            request.analysis_mode is AnalysisMode.AUTO_MONITOR
            and request.payload.analysis_severity is AnalysisSeverity.NORMAL
        ):
            raise ServiceError(
                status_code=422,
                code=ErrorCode.INPUT_SCHEMA_INVALID,
                message="NORMAL 자동 모니터링 결과는 AI 생성을 호출하지 않습니다.",
                validation_codes=["AUTO_MONITOR_NORMAL_MODEL_CALL_FORBIDDEN"],
            )
        if not any(
            metric.availability is MetricAvailability.AVAILABLE
            for metric in request.payload.metrics
        ):
            raise ServiceError(
                status_code=422,
                code=ErrorCode.INSUFFICIENT_DATA,
                message="생성에 사용할 수 있는 경제 지표가 부족합니다.",
                validation_codes=["NO_AVAILABLE_METRIC"],
            )
    elif isinstance(request, EconomicEventGenerationRequest):
        _validate_event_grade_policy(request)


def _validate_event_grade_policy(request: EconomicEventGenerationRequest) -> None:
    template = request.payload.template_type
    allowed_by_grade = {
        4: {EventTemplateType.PRICE, EventTemplateType.SUPPLY, EventTemplateType.PUBLIC_FUND},
        5: {
            EventTemplateType.PRICE,
            EventTemplateType.SUPPLY,
            EventTemplateType.PUBLIC_FUND,
            EventTemplateType.TAX,
            EventTemplateType.INTEREST_RATE,
        },
        6: set(EventTemplateType),
    }
    if template not in allowed_by_grade[request.grade]:
        raise ServiceError(
            status_code=422,
            code=ErrorCode.OUTPUT_SAFETY_REJECTED,
            message="학년 정책에서 허용하지 않는 경제 이벤트 템플릿입니다.",
            validation_codes=["GRADE_POLICY_VIOLATION"],
        )
    if template is EventTemplateType.INTEREST_RATE:
        scope = request.payload.interest_rate_scope
        if request.grade == 5 and scope is InterestRateScope.LOAN:
            raise ServiceError(
                status_code=422,
                code=ErrorCode.OUTPUT_SAFETY_REJECTED,
                message="학년 정책에서 허용하지 않는 금리 범위입니다.",
                validation_codes=["GRADE_POLICY_VIOLATION"],
            )


def validate_output_policy(request: GenerationRequest, output: GenerationOutput) -> None:
    codes: list[str] = []
    if isinstance(request, BalancingGenerationRequest):
        if isinstance(request.payload, SetupPrecheckPayload) and isinstance(
            output, SetupPrecheckOutput
        ):
            codes.extend(_validate_setup_precheck(request.payload, output))
        elif isinstance(output, BalancingOutput):
            codes.extend(_validate_balancing(request, output))
        else:
            codes.append("OUTPUT_TYPE_MISMATCH")
    elif isinstance(request, TeacherChatGenerationRequest) and isinstance(
        output, TeacherChatOutput
    ):
        codes.extend(_validate_teacher_chat(request, output))
    elif isinstance(request, EconomicEventGenerationRequest) and isinstance(
        output, EconomicEventOutput
    ):
        codes.extend(_validate_economic_event(request, output))
    elif isinstance(request, QuizDraftGenerationRequest) and isinstance(output, QuizDraftOutput):
        codes.extend(_validate_quiz(request, output))
    else:
        codes.append("OUTPUT_TYPE_MISMATCH")

    safety_codes = _scan_safety(output)
    if safety_codes:
        raise ServiceError(
            status_code=422,
            code=ErrorCode.OUTPUT_SAFETY_REJECTED,
            message="AI 출력이 안전 경계를 충족하지 않습니다.",
            validation_codes=safety_codes,
        )
    if codes:
        raise ServiceError(
            status_code=422,
            code=ErrorCode.OUTPUT_SCHEMA_INVALID,
            message="AI 출력이 구조화 응답 계약을 충족하지 않습니다.",
            validation_codes=codes,
        )


def _validate_balancing(request: BalancingGenerationRequest, output: BalancingOutput) -> list[str]:
    codes: list[str] = []
    metric_codes = {metric.code.value for metric in request.payload.metrics}
    action_by_id = {action.action_id: action for action in request.payload.allowed_actions}
    if not set(output.evidence_metric_codes) <= metric_codes:
        codes.append("FABRICATED_EVIDENCE")
    for proposal in output.proposals:
        action = action_by_id.get(proposal.action_id)
        if action is None:
            codes.append("UNKNOWN_ACTION_ID")
        elif not action.min_value <= proposal.proposed_value <= action.max_value:
            codes.append("PROPOSED_VALUE_OUT_OF_RANGE")
        if not set(proposal.evidence_metric_codes) <= metric_codes:
            codes.append("FABRICATED_EVIDENCE")
    return _unique(codes)


def _validate_setup_precheck(
    payload: SetupPrecheckPayload, output: SetupPrecheckOutput
) -> list[str]:
    codes: list[str] = []
    if output.context_type is not payload.context.context_type:
        codes.append("SETUP_CONTEXT_MISMATCH")

    candidate_by_id = {candidate.action_id: candidate for candidate in payload.candidates}
    explanation_ids = [item.action_id for item in output.candidate_explanations]
    if len(explanation_ids) != len(set(explanation_ids)):
        codes.append("DUPLICATE_ACTION_ID")
    if set(explanation_ids) != set(candidate_by_id):
        codes.append("SETUP_CANDIDATE_MISMATCH")
    for explanation in output.candidate_explanations:
        candidate = candidate_by_id.get(explanation.action_id)
        if candidate is None:
            continue
        if explanation.proposed_value != candidate.proposed_value:
            codes.append("PROPOSED_VALUE_CHANGED")
        if not set(explanation.evidence_reason_codes) <= set(candidate.reason_codes):
            codes.append("FABRICATED_EVIDENCE")

    check_by_id = {check.check_id: check for check in payload.checks}
    check_explanation_ids = [item.check_id for item in output.check_explanations]
    if len(check_explanation_ids) != len(set(check_explanation_ids)):
        codes.append("DUPLICATE_CHECK_ID")
    if set(check_explanation_ids) != set(check_by_id):
        codes.append("PREFLIGHT_CHECK_MISMATCH")
    for explanation in output.check_explanations:
        check = check_by_id.get(explanation.check_id)
        if check is None:
            continue
        if explanation.severity is not check.severity:
            codes.append("PREFLIGHT_SEVERITY_CHANGED")
        if explanation.reason_code != check.reason_code:
            codes.append("PREFLIGHT_REASON_CHANGED")
    return _unique(codes)


def _validate_teacher_chat(
    request: TeacherChatGenerationRequest, output: TeacherChatOutput
) -> list[str]:
    codes: list[str] = []
    fact_ids = {fact.fact_id for fact in request.payload.facts}
    if not set(output.evidence_fact_ids) <= fact_ids:
        codes.append("FABRICATED_EVIDENCE")
    if output.period != request.payload.period:
        codes.append("PERIOD_CONTEXT_MISMATCH")
    if output.data_as_of != request.payload.period.data_as_of:
        codes.append("DATA_AS_OF_CONTEXT_MISMATCH")
    return codes


def _validate_economic_event(
    request: EconomicEventGenerationRequest, output: EconomicEventOutput
) -> list[str]:
    codes: list[str] = []
    if output.template_type is not request.payload.template_type:
        codes.append("EVENT_TEMPLATE_MISMATCH")
    input_context = request.payload.investment_news_context
    output_binding = output.investment_news_binding
    if input_context is not None and output_binding is not None:
        if (
            output_binding.scenario_version_id != input_context.scenario_version_id
            or output_binding.asset_id != input_context.asset_id
            or output_binding.price_step_id != input_context.price_step_id
            or output_binding.current_step_index != input_context.current_step_index
        ):
            codes.append("INVESTMENT_NEWS_BINDING_MISMATCH")
    text = " ".join(_iter_strings(output))
    if any(effect.casefold() in text.casefold() for effect in request.payload.prohibited_effects):
        codes.append("FORBIDDEN_EVENT_EFFECT")
    if re.search(r"\d", text):
        codes.append("FORBIDDEN_EVENT_NUMERIC_EFFECT")
    return _unique(codes)


def _validate_quiz(request: QuizDraftGenerationRequest, output: QuizDraftOutput) -> list[str]:
    codes: list[str] = []
    if output.category is not request.payload.category:
        codes.append("QUIZ_CATEGORY_MISMATCH")
    if len(output.questions) != request.payload.count:
        codes.append("QUIZ_COUNT_MISMATCH")
    if any(
        question.question_type is not request.payload.question_type for question in output.questions
    ):
        codes.append("QUIZ_TYPE_MISMATCH")
    if any(question.difficulty is not request.payload.difficulty for question in output.questions):
        codes.append("QUIZ_DIFFICULTY_MISMATCH")
    allowed_concepts = set(request.payload.concepts)
    if any(not set(question.concept_tags) <= allowed_concepts for question in output.questions):
        codes.append("FABRICATED_CONCEPT_TAG")
    return codes


def _scan_safety(output: BaseModel) -> list[str]:
    text = " ".join(_iter_strings(output))
    codes: list[str] = []
    if UUID_PATTERN.search(text) or EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text):
        codes.append("SENSITIVE_DATA_DETECTED")
    if TOKEN_PATTERN.search(text):
        codes.append("SECRET_PATTERN_DETECTED")
    if any(phrase in text for phrase in APPROVAL_BOUNDARY_PHRASES):
        codes.append("APPROVAL_BOUNDARY_VIOLATION")
    if any(phrase in text for phrase in FINANCIAL_ADVICE_PHRASES):
        codes.append("FINANCIAL_ADVICE_DETECTED")
    return codes


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


def _unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))
