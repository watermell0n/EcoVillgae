from __future__ import annotations

import re
from collections.abc import Iterable

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.models import (
    ErrorCode,
    TeacherChatGenerationRequest,
    TeacherChatLimitationCode,
    TeacherChatOutput,
    TeacherChatResponseMode,
)

EMAIL_PATTERN = re.compile(
    r"(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
    r"(?![A-Za-z0-9.-])"
)
PHONE_PATTERN = re.compile(r"(?<!\d)01[016789][ -]?\d{3,4}[ -]?\d{4}(?!\d)")
UUID_PATTERN = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-"
    r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b"
)
SECRET_PATTERN = re.compile(
    r"(?i)(?:bearer\s+[A-Za-z0-9._~+/=-]+|access[_ -]?token|refresh[_ -]?token|"
    r"api[_ -]?key|비밀번호|토큰\s*(?:값|키))"
)

LIMITATION_RULES: tuple[
    tuple[TeacherChatLimitationCode, tuple[re.Pattern[str], ...]], ...
] = (
    (
        TeacherChatLimitationCode.PERSONAL_ANALYSIS_NOT_SUPPORTED,
        (
            re.compile(
                r"학생별|개별\s*학생|특정\s*학생|학생\s*이름|개인\s*(?:잔액|거래)|"
                r"누가.{0,12}(?:가장|많|적|높|낮)|누구의"
            ),
        ),
    ),
    (
        TeacherChatLimitationCode.REAL_FINANCIAL_ADVICE_NOT_SUPPORTED,
        (
            re.compile(r"(?:실제|현실).{0,15}(?:주식|코인|펀드|투자|대출)"),
            re.compile(r"(?:주식|코인|펀드).{0,15}(?:사야|살까|매수|추천)"),
            re.compile(r"수익.{0,10}보장"),
        ),
    ),
    (
        TeacherChatLimitationCode.FUTURE_PREDICTION_NOT_SUPPORTED,
        (
            re.compile(r"(?:미래|다음\s*단계).{0,15}(?:가격|수익|수익률)"),
            re.compile(r"(?:가격|수익률).{0,15}(?:오를|내릴|예측|전망)"),
        ),
    ),
    (
        TeacherChatLimitationCode.READ_ONLY_BOUNDARY,
        (
            re.compile(
                r"(?:세율|금리|급여|보상|가격|재고|잔액|계좌|정책|설정).{0,20}"
                r"(?:바꿔|변경해|적용해|조정해|수정해|설정해)"
            ),
            re.compile(r"(?:검토\s*없이|바로|대신).{0,12}(?:승인|게시|실행|적용)"),
        ),
    ),
    (
        TeacherChatLimitationCode.PROMPT_INJECTION_BLOCKED,
        (
            re.compile(r"이전\s*(?:지시|명령).{0,8}무시"),
            re.compile(r"(?:시스템|숨겨진)\s*(?:프롬프트|지시)"),
            re.compile(r"(?:비밀|내부\s*지시).{0,12}(?:보여|출력|공개)"),
        ),
    ),
)

LIMITATION_MESSAGES = {
    TeacherChatLimitationCode.PERSONAL_ANALYSIS_NOT_SUPPORTED: (
        "요청하신 범위는 제공할 수 없습니다. 학급 전체의 집계 흐름에 관해 질문해 주세요."
    ),
    TeacherChatLimitationCode.REAL_FINANCIAL_ADVICE_NOT_SUPPORTED: (
        "현실의 금융 선택을 지시할 수 없습니다. 수업 안의 학습용 경제 현상은 설명할 수 있습니다."
    ),
    TeacherChatLimitationCode.FUTURE_PREDICTION_NOT_SUPPORTED: (
        "공개되지 않은 이후 결과는 예측하지 않습니다. 현재 공개된 집계 사실은 설명할 수 있습니다."
    ),
    TeacherChatLimitationCode.READ_ONLY_BOUNDARY: (
        "이 챗봇은 학급 경제를 설명하는 읽기 전용 기능입니다. 변경은 교사 화면에서 검토해 주세요."
    ),
    TeacherChatLimitationCode.PROMPT_INJECTION_BLOCKED: (
        "승인된 학급 집계 자료의 범위에서만 답할 수 있습니다. 학급 경제 현황을 질문해 주세요."
    ),
}


def teacher_chat_policy_output(
    request: TeacherChatGenerationRequest,
) -> TeacherChatOutput | None:
    """외부 모델에 보내면 안 되는 입력을 막고 제한 요청은 결정적으로 답한다."""

    _validate_sensitive_input(request)
    question = request.payload.question
    limitation_codes = [
        code
        for code, patterns in LIMITATION_RULES
        if any(pattern.search(question) for pattern in patterns)
    ]
    if not limitation_codes:
        return None
    answer = " ".join(LIMITATION_MESSAGES[code] for code in limitation_codes)
    return TeacherChatOutput(
        response_mode=TeacherChatResponseMode.LIMITED,
        answer=answer,
        evidence_fact_ids=[],
        causes=[],
        recommendations=[],
        limitation_codes=limitation_codes,
        period=request.payload.period,
        data_as_of=request.payload.period.data_as_of,
    )


def _validate_sensitive_input(request: TeacherChatGenerationRequest) -> None:
    text = " ".join(_iter_untrusted_text(request))
    codes: list[str] = []
    if UUID_PATTERN.search(text) or EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text):
        codes.append("SENSITIVE_INPUT_DETECTED")
    if SECRET_PATTERN.search(text):
        codes.append("SECRET_INPUT_DETECTED")
    if not codes:
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.INPUT_SCHEMA_INVALID,
        message="교사용 챗봇 입력에 허용되지 않는 민감정보가 포함되어 있습니다.",
        retryable=False,
        validation_codes=list(dict.fromkeys(codes)),
        validation_stage="CONTENT_SAFETY",
    )


def _iter_untrusted_text(request: TeacherChatGenerationRequest) -> Iterable[str]:
    yield request.payload.question
    for fact in request.payload.facts:
        yield fact.label
        if isinstance(fact.value, str):
            yield fact.value
    for change in request.payload.recent_policy_changes:
        yield change.summary
