from __future__ import annotations

import re

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.models import (
    EconomicEventGenerationRequest,
    EconomicEventOutput,
    ErrorCode,
    EventTemplateType,
)

COMPANY_INPUT_PATTERN = re.compile(
    r"(?:실제\s*)?(?:회사|기업|주식회사|법인|대표이사|상장사|종목|주식)"
)
FUTURE_INPUT_PATTERN = re.compile(
    r"(?:다음\s*단계|미공개|미래|앞으로|예정|예측|전망|오를|내릴|상승할|하락할)"
)
COMPANY_OUTPUT_PATTERN = re.compile(
    r"(?:회사|기업|주식회사|법인|대표이사|매출|영업이익|순이익|재무제표|실적|업종|상장|배당|주가)"
)
PRICE_OUTPUT_PATTERN = re.compile(r"(?:가격|시세).{0,16}(?:원|화폐|코인|포인트|얼마)")
TRADING_ADVICE_PATTERN = re.compile(
    r"(?:수익률|수익\s*보장|매수|매도|사세요|사야|팔아야|투자하세요|투자\s*추천)"
)
FUTURE_OUTPUT_PATTERN = re.compile(
    r"(?:다음\s*단계|미공개|미래|앞으로|예정|예측|전망|오를|내릴|상승할|하락할)"
)
REVEAL_CLAIM_PATTERN = re.compile(
    r"(?:승인(?:됐|되었|받았)|공개(?:됐|되었|중)|게시(?:됐|되었)|학생에게\s*공개)"
)


def validate_investment_news_input(request: EconomicEventGenerationRequest) -> None:
    """투자 뉴스가 가상 자산과 목표 단계의 사실만 받도록 검사한다."""

    if request.payload.template_type is not EventTemplateType.INVESTMENT_NEWS:
        return
    context = request.payload.investment_news_context
    if context is None:  # 모델의 교차 필드 검증이 먼저 처리하지만 타입 안정성을 유지한다.
        return
    request_text = " ".join(
        value
        for value in (*request.payload.effect_summary, request.payload.teacher_context)
        if value is not None
    )
    codes: list[str] = []
    if COMPANY_INPUT_PATTERN.search(context.asset_name) or COMPANY_INPUT_PATTERN.search(
        request_text
    ):
        codes.append("INVESTMENT_COMPANY_INPUT_FORBIDDEN")
    if FUTURE_INPUT_PATTERN.search(request_text):
        codes.append("INVESTMENT_FUTURE_INFORMATION_INPUT")
    if not codes:
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.INPUT_SCHEMA_INVALID,
        message="투자 뉴스 입력은 가상 자산의 목표 단계까지 확인된 사실만 포함할 수 있습니다.",
        retryable=False,
        validation_codes=list(dict.fromkeys(codes)),
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
    if COMPANY_OUTPUT_PATTERN.search(text):
        codes.append("INVESTMENT_COMPANY_GENERATION")
    if PRICE_OUTPUT_PATTERN.search(text):
        codes.append("INVESTMENT_PRICE_GENERATION")
    if TRADING_ADVICE_PATTERN.search(text):
        codes.append("INVESTMENT_TRADING_ADVICE")
    if FUTURE_OUTPUT_PATTERN.search(text):
        codes.append("INVESTMENT_FUTURE_INFORMATION")
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
