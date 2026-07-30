from __future__ import annotations

import re

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.features.investment_news import (
    investment_news_approval_boundary_codes,
    investment_news_grade_and_effect_codes,
    validate_investment_news_input,
)
from ecovillage_ai.features.setup_precheck import PROMPT_INJECTION_PATTERNS
from ecovillage_ai.features.teacher_chat import (
    EMAIL_PATTERN,
    PHONE_PATTERN,
    SECRET_PATTERN,
    UUID_PATTERN,
)
from ecovillage_ai.models import (
    CourseScale,
    EconomicEventGenerationRequest,
    EconomicEventOutput,
    ErrorCode,
    EventTemplateType,
    InterestRateScope,
)

ALLOWED_TEMPLATES_BY_GRADE = {
    4: {
        EventTemplateType.PRICE,
        EventTemplateType.SUPPLY,
        EventTemplateType.PUBLIC_FUND,
    },
    5: {
        EventTemplateType.PRICE,
        EventTemplateType.SUPPLY,
        EventTemplateType.PUBLIC_FUND,
        EventTemplateType.TAX,
        EventTemplateType.INTEREST_RATE,
    },
    6: set(EventTemplateType),
}
GRADE_TEXT_LIMITS = {
    4: (80, 700, 120, 260, 180),
    5: (100, 1000, 150, 340, 230),
    6: (120, 1400, 180, 440, 300),
}
FORBIDDEN_EFFECT_PATTERNS = (
    re.compile(r"특정\s*학생.{0,20}(?:손익|이익|손해|보상|차감|지급)"),
    re.compile(r"(?:강제\s*대출|대출.{0,12}강제)"),
    re.compile(r"(?:개인\s*잔액|학생\s*계좌).{0,20}(?:직접|차감|변경|회수)"),
    re.compile(r"(?:음수\s*잔액|잔액.{0,12}음수)"),
)
UNSAFE_EVENT_CONTENT_PATTERN = re.compile(
    r"(?:병신|등신|멍청이?|바보|찌질이?|왕따|죽여|때려|성행위|야한|"
    r"개꿀|노잼|킹받|[ㅋㅎ]{4,})"
)
APPROVAL_BYPASS_PATTERNS = (
    re.compile(r"(?:교사\s*)?승인.{0,8}(?:없이|생략)"),
    re.compile(r"(?:자동|즉시|바로).{0,8}(?:실행|공개|게시|적용)"),
    re.compile(r"(?:이미|방금).{0,8}(?:실행|공개|게시|적용|승인)(?:됐|되었|했)"),
)


def validate_economic_event_input(request: EconomicEventGenerationRequest) -> None:
    """외부 모델을 호출하기 전에 이벤트 정책과 신뢰 입력의 안전성을 검사한다."""

    _validate_free_text(request)
    validate_investment_news_input(request)
    payload = request.payload
    codes: list[str] = []
    if payload.course_scale is CourseScale.ONE_SHOT:
        codes.append("ONE_SHOT_EVENT_NOT_ALLOWED")
    if payload.template_type not in ALLOWED_TEMPLATES_BY_GRADE[request.grade]:
        codes.append("GRADE_POLICY_VIOLATION")
    if (
        payload.template_type is EventTemplateType.INTEREST_RATE
        and request.grade == 5
        and payload.interest_rate_scope is InterestRateScope.LOAN
    ):
        codes.append("GRADE_POLICY_VIOLATION")
    if not codes:
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.OUTPUT_SAFETY_REJECTED,
        message="요청한 경제 이벤트는 현재 학년 또는 코스에서 사용할 수 없습니다.",
        retryable=False,
        validation_codes=list(dict.fromkeys(codes)),
        validation_stage="GRADE_AND_EFFECT",
    )


def event_grade_and_effect_codes(
    request: EconomicEventGenerationRequest,
    output: EconomicEventOutput,
) -> list[str]:
    text = " ".join(event_natural_language(output))
    normalized = text.casefold()
    codes: list[str] = []
    if any(effect.casefold() in normalized for effect in request.payload.prohibited_effects):
        codes.append("FORBIDDEN_EVENT_EFFECT")
    if any(pattern.search(text) for pattern in FORBIDDEN_EFFECT_PATTERNS):
        codes.append("FORBIDDEN_EVENT_EFFECT")
    if re.search(r"\d", text):
        codes.append("FORBIDDEN_EVENT_NUMERIC_EFFECT")
    codes.extend(investment_news_grade_and_effect_codes(request, output))

    title_limit, narrative_limit, choice_limit, consequence_limit, progress_limit = (
        GRADE_TEXT_LIMITS[request.grade]
    )
    if (
        len(output.title) > title_limit
        or len(output.narrative) > narrative_limit
        or any(len(choice.label) > choice_limit for choice in output.choices)
        or any(
            len(choice.consequence_text) > consequence_limit
            for choice in output.choices
        )
        or any(len(message) > progress_limit for message in output.progress_messages)
    ):
        codes.append("EVENT_GRADE_TEXT_TOO_COMPLEX")
    return list(dict.fromkeys(codes))


def event_content_safety_codes(output: EconomicEventOutput) -> list[str]:
    text = " ".join(event_natural_language(output))
    if UNSAFE_EVENT_CONTENT_PATTERN.search(text):
        return ["EVENT_UNSAFE_CONTENT"]
    return []


def event_approval_boundary_codes(output: EconomicEventOutput) -> list[str]:
    text = " ".join(event_natural_language(output))
    codes = investment_news_approval_boundary_codes(output)
    if any(pattern.search(text) for pattern in APPROVAL_BYPASS_PATTERNS):
        codes.append("APPROVAL_BOUNDARY_VIOLATION")
    return list(dict.fromkeys(codes))


def event_natural_language(output: EconomicEventOutput) -> list[str]:
    values = [
        output.title,
        output.narrative,
        *output.progress_messages,
        *(choice.label for choice in output.choices),
        *(choice.consequence_text for choice in output.choices),
    ]
    if output.teacher_explanation is not None:
        values.append(output.teacher_explanation)
    return values


def _validate_free_text(request: EconomicEventGenerationRequest) -> None:
    payload = request.payload
    text = " ".join(
        value
        for value in (
            *payload.effect_summary,
            *payload.prohibited_effects,
            payload.teacher_context,
            (
                payload.investment_news_context.asset_name
                if payload.investment_news_context is not None
                else None
            ),
        )
        if value is not None
    )
    codes: list[str] = []
    if UUID_PATTERN.search(text) or EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text):
        codes.append("SENSITIVE_INPUT_DETECTED")
    if SECRET_PATTERN.search(text):
        codes.append("SECRET_INPUT_DETECTED")
    if any(pattern.search(text) for pattern in PROMPT_INJECTION_PATTERNS):
        codes.append("ECONOMIC_EVENT_PROMPT_INJECTION_INPUT")
    if not codes:
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.INPUT_SCHEMA_INVALID,
        message="경제 이벤트 입력에 허용되지 않는 내용이 포함되어 있습니다.",
        retryable=False,
        validation_codes=list(dict.fromkeys(codes)),
        validation_stage="CONTENT_SAFETY",
    )
