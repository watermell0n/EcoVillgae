from __future__ import annotations

import re

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.models import (
    EconomicEventGenerationRequest,
    EconomicEventOutput,
    ErrorCode,
    EventTemplateType,
    InvestmentMode,
)

FUTURE_INPUT_PATTERN = re.compile(
    r"(?:다음\s*단계|미공개|미래|앞으로\s*예정|예측|전망|오를|내릴|상승|하락)"
)
INVENTED_COMPANY_PATTERN = re.compile(
    r"(?:새로\s*만든|새로운|다른)\s*(?:회사|기업)|새\s*(?:회사|기업)"
)
PRICE_OUTPUT_PATTERN = re.compile(r"(?:가격|시세).{0,16}(?:원|비빔|코인|포인트|얼마)")
TRADING_ADVICE_PATTERN = re.compile(
    r"(?:수익률|수익\s*보장|매수|매도|사세요|팔아야|투자하세요|투자\s*추천)"
)
FUTURE_OUTPUT_PATTERN = re.compile(
    r"(?:다음\s*(?:날|단계)|미공개|미래|앞으로).{0,24}"
    r"(?:오를|내릴|상승|하락|수익|손실|뛸|떨어질|결과)"
)
DIRECT_RESULT_PATTERN = re.compile(
    r"(?:반드시|분명히|확실히|곧).{0,12}(?:오를|내릴|상승|하락|수익|손실)"
)
REAL_MARKET_PATTERN = re.compile(
    r"(?:코스피|코스닥|나스닥|다우\s*존스|S\s*&\s*P|에스앤피|삼성전자|테슬라|애플|엔비디아)",
    re.IGNORECASE,
)
REVEAL_CLAIM_PATTERN = re.compile(
    r"(?:승인(?:이\s*)?(?:완료|되었|됐)|공개(?:가\s*)?(?:완료|되었|됐)|게시(?:가\s*)?(?:완료|되었|됐)|학생에게\s*공개)"
)


def validate_investment_news_input(request: EconomicEventGenerationRequest) -> None:
    """구조화된 숨은 방향 외에 교사 자유 입력으로 미래 결과가 새지 않게 한다."""

    if request.payload.template_type is not EventTemplateType.INVESTMENT_NEWS:
        return
    context = request.payload.investment_news_context
    if context is None:  # 모델 교차 필드 검증이 먼저 처리하지만 타입 안정성을 유지한다.
        return
    request_text = " ".join(
        value
        for value in (*request.payload.effect_summary, request.payload.teacher_context)
        if value is not None
    )
    if not FUTURE_INPUT_PATTERN.search(request_text):
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.INPUT_SCHEMA_INVALID,
        message="투자 이야기의 다음 결과는 구조화된 내부 방향으로만 전달할 수 있습니다.",
        retryable=False,
        validation_codes=["INVESTMENT_FUTURE_INFORMATION_INPUT"],
        validation_stage="CONTENT_SAFETY",
    )


def investment_news_grade_and_effect_codes(
    request: EconomicEventGenerationRequest,
    output: EconomicEventOutput,
) -> list[str]:
    if request.payload.template_type is not EventTemplateType.INVESTMENT_NEWS:
        return []
    context = request.payload.investment_news_context
    if context is None:
        return []
    text = " ".join(_natural_language(output))
    codes: list[str] = []
    if context.asset_name not in text and context.asset_symbol not in text:
        codes.append("INVESTMENT_ASSET_REFERENCE_MISSING")
    if INVENTED_COMPANY_PATTERN.search(text):
        codes.append("INVESTMENT_COMPANY_GENERATION")
    if PRICE_OUTPUT_PATTERN.search(text):
        codes.append("INVESTMENT_PRICE_GENERATION")
    if TRADING_ADVICE_PATTERN.search(text):
        codes.append("INVESTMENT_TRADING_ADVICE")
    if FUTURE_OUTPUT_PATTERN.search(text) or DIRECT_RESULT_PATTERN.search(text):
        codes.append("INVESTMENT_FUTURE_INFORMATION")
    if REAL_MARKET_PATTERN.search(text):
        codes.append("INVESTMENT_REFERENCE_LEAK")

    choice_keys = {choice.choice_key for choice in output.choices}
    expected_keys = (
        {"INVEST", "SKIP"}
        if context.investment_mode is InvestmentMode.SHORT_TERM
        else {"INVEST_MORE", "HOLD", "SELL"}
    )
    if choice_keys != expected_keys:
        codes.append("INVESTMENT_MODE_CHOICES_MISMATCH")
    return list(dict.fromkeys(codes))


def investment_news_approval_boundary_codes(output: EconomicEventOutput) -> list[str]:
    if output.template_type is not EventTemplateType.INVESTMENT_NEWS:
        return []
    text = " ".join(_natural_language(output))
    if REVEAL_CLAIM_PATTERN.search(text):
        return ["APPROVAL_BOUNDARY_VIOLATION"]
    return []


def _natural_language(output: EconomicEventOutput) -> list[str]:
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
