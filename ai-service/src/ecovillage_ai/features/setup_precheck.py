from __future__ import annotations

import re

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.features.teacher_chat import (
    EMAIL_PATTERN,
    PHONE_PATTERN,
    SECRET_PATTERN,
    UUID_PATTERN,
)
from ecovillage_ai.models import (
    ErrorCode,
    PreflightSeverity,
    SetupPrecheckOutput,
    SetupPrecheckPayload,
)

PROMPT_INJECTION_PATTERNS = (
    re.compile(r"이전\s*(?:지시|명령).{0,8}무시"),
    re.compile(r"(?:시스템|숨겨진)\s*(?:프롬프트|지시)"),
    re.compile(r"(?:비밀|내부\s*지시).{0,12}(?:보여|출력|공개)"),
)
SEVERITY_PRIORITY = {
    PreflightSeverity.PASS: 0,
    PreflightSeverity.WARN: 1,
    PreflightSeverity.BLOCK: 2,
}


def validate_setup_precheck_input(payload: SetupPrecheckPayload) -> None:
    """상품명과 규칙 메시지에 섞인 민감정보·지시 탈취를 공급자 호출 전에 막는다."""

    text = " ".join(
        (
            *(candidate.label for candidate in payload.candidates),
            *(check.message for check in payload.checks),
        )
    )
    codes: list[str] = []
    if UUID_PATTERN.search(text) or EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text):
        codes.append("SENSITIVE_INPUT_DETECTED")
    if SECRET_PATTERN.search(text):
        codes.append("SECRET_INPUT_DETECTED")
    if any(pattern.search(text) for pattern in PROMPT_INJECTION_PATTERNS):
        codes.append("SETUP_PROMPT_INJECTION_INPUT")
    if not codes:
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.INPUT_SCHEMA_INVALID,
        message="설정 추천 입력에 허용되지 않는 내용이 포함되어 있습니다.",
        retryable=False,
        validation_codes=list(dict.fromkeys(codes)),
        validation_stage="CONTENT_SAFETY",
    )


def highest_preflight_severity(payload: SetupPrecheckPayload) -> PreflightSeverity:
    return max(
        (check.severity for check in payload.checks),
        key=SEVERITY_PRIORITY.__getitem__,
    )


def preflight_guidance_quality_codes(
    payload: SetupPrecheckPayload,
    output: SetupPrecheckOutput,
) -> list[str]:
    codes: list[str] = []
    explanation_by_id = {
        explanation.check_id: explanation for explanation in output.check_explanations
    }
    candidate_by_id = {
        explanation.action_id: explanation
        for explanation in output.candidate_explanations
    }
    for check in payload.checks:
        explanation = explanation_by_id.get(check.check_id)
        if explanation is None:
            continue
        if (
            check.severity is PreflightSeverity.PASS
            and explanation.correction_guidance is not None
        ):
            codes.append("PASS_CORRECTION_GUIDANCE_FORBIDDEN")
        if (
            check.severity in {PreflightSeverity.WARN, PreflightSeverity.BLOCK}
            and explanation.correction_guidance is None
        ):
            codes.append("PREFLIGHT_CORRECTION_GUIDANCE_REQUIRED")
        if check.severity in {PreflightSeverity.WARN, PreflightSeverity.BLOCK}:
            for action_id in check.subject_action_ids:
                candidate = candidate_by_id.get(action_id)
                if candidate is not None and not candidate.cautions:
                    codes.append("PREFLIGHT_CANDIDATE_CAUTION_REQUIRED")
    return list(dict.fromkeys(codes))
