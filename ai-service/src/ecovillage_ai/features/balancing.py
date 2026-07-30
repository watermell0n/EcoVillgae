from __future__ import annotations

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.features.setup_precheck import PROMPT_INJECTION_PATTERNS
from ecovillage_ai.features.teacher_chat import (
    EMAIL_PATTERN,
    PHONE_PATTERN,
    SECRET_PATTERN,
    UUID_PATTERN,
)
from ecovillage_ai.models import (
    ActionTargetType,
    AnalysisMode,
    AnalysisSeverity,
    BalancingGenerationRequest,
    BalancingPayload,
    ErrorCode,
    MetricAvailability,
    MetricCode,
    MetricDirection,
    MetricState,
    MetricUnit,
    SampleStatus,
)

METRIC_UNITS = {
    MetricCode.ISSUANCE_AMOUNT: MetricUnit.AMOUNT,
    MetricCode.RECOVERY_AMOUNT: MetricUnit.AMOUNT,
    **{
        code: MetricUnit.RATE_BPS
        for code in MetricCode
        if code not in {MetricCode.ISSUANCE_AMOUNT, MetricCode.RECOVERY_AMOUNT}
    },
}
ACTION_UNITS = {
    ActionTargetType.PRODUCT_PRICE: MetricUnit.AMOUNT,
    ActionTargetType.PRODUCT_STOCK: MetricUnit.COUNT,
    ActionTargetType.ACTIVITY_REWARD: MetricUnit.AMOUNT,
    ActionTargetType.GAME_REWARD: MetricUnit.AMOUNT,
    ActionTargetType.QUIZ_REWARD: MetricUnit.AMOUNT,
    ActionTargetType.TAX_RATE: MetricUnit.RATE_BPS,
    ActionTargetType.INFLATION_RATE: MetricUnit.RATE_BPS,
    ActionTargetType.DEPOSIT_RATE: MetricUnit.RATE_BPS,
    ActionTargetType.SAVINGS_RATE: MetricUnit.RATE_BPS,
    ActionTargetType.LOAN_RATE: MetricUnit.RATE_BPS,
}
SEVERITY_PRIORITY = {
    AnalysisSeverity.NORMAL: 0,
    AnalysisSeverity.OBSERVE: 1,
    AnalysisSeverity.WARN: 2,
}


def validate_balancing_input(request: BalancingGenerationRequest) -> None:
    payload = request.payload
    if not isinstance(payload, BalancingPayload):
        return

    _validate_free_text(payload)
    if payload.sample.status is SampleStatus.INSUFFICIENT:
        codes: list[str] = []
        if payload.sample.active_students < 5:
            codes.append("ACTIVE_STUDENTS_LT_5")
        if payload.sample.related_transactions < 10:
            codes.append("RELATED_TRANSACTIONS_LT_10")
        raise ServiceError(
            status_code=422,
            code=ErrorCode.INSUFFICIENT_DATA,
            message="경제 분석에 필요한 학급 표본이 부족합니다.",
            retryable=False,
            validation_codes=codes,
            validation_stage="EVIDENCE",
        )

    available = [
        metric
        for metric in payload.metrics
        if metric.availability is MetricAvailability.AVAILABLE
    ]
    if not available:
        raise ServiceError(
            status_code=422,
            code=ErrorCode.INSUFFICIENT_DATA,
            message="생성에 사용할 수 있는 경제 지표가 부족합니다.",
            retryable=False,
            validation_codes=["NO_AVAILABLE_METRIC"],
            validation_stage="EVIDENCE",
        )

    if (
        request.analysis_mode is AnalysisMode.AUTO_MONITOR
        and payload.analysis_severity is AnalysisSeverity.NORMAL
    ):
        raise ServiceError(
            status_code=422,
            code=ErrorCode.INPUT_SCHEMA_INVALID,
            message="NORMAL 자동 모니터링 결과는 AI 생성을 호출하지 않습니다.",
            retryable=False,
            validation_codes=["AUTO_MONITOR_NORMAL_MODEL_CALL_FORBIDDEN"],
            validation_stage="CONTEXT",
        )

    if (
        request.analysis_mode is AnalysisMode.AUTO_MONITOR
        and not payload.allowed_actions
    ):
        raise ServiceError(
            status_code=422,
            code=ErrorCode.INPUT_SCHEMA_INVALID,
            message="자동 모니터링은 허용된 조정 후보가 있을 때만 AI를 호출합니다.",
            retryable=False,
            validation_codes=["AUTO_MONITOR_ACTION_REQUIRED"],
            validation_stage="CONTEXT",
        )

    codes = _semantic_input_codes(payload)
    if codes:
        raise ServiceError(
            status_code=422,
            code=ErrorCode.INPUT_SCHEMA_INVALID,
            message="경제 분석 입력의 지표·조정 문맥이 일치하지 않습니다.",
            retryable=False,
            validation_codes=codes,
            validation_stage="CONTEXT",
        )


def eligible_evidence_codes(request: BalancingGenerationRequest) -> set[str]:
    payload = request.payload
    if not isinstance(payload, BalancingPayload):
        return set()
    eligible_states = (
        {MetricState.OBSERVE, MetricState.WARN}
        if request.analysis_mode is AnalysisMode.AUTO_MONITOR
        else {MetricState.NORMAL, MetricState.OBSERVE, MetricState.WARN}
    )
    return {
        metric.code.value
        for metric in payload.metrics
        if metric.availability is MetricAvailability.AVAILABLE
        and metric.state in eligible_states
    }


def _validate_free_text(payload: BalancingPayload) -> None:
    text = " ".join(
        value
        for value in (
            *(metric.subject_label for metric in payload.metrics),
            *(metric.unavailable_reason for metric in payload.metrics),
            *(action.label for action in payload.allowed_actions),
        )
        if value is not None
    )
    codes: list[str] = []
    if UUID_PATTERN.search(text) or EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text):
        codes.append("SENSITIVE_INPUT_DETECTED")
    if SECRET_PATTERN.search(text):
        codes.append("SECRET_INPUT_DETECTED")
    if any(pattern.search(text) for pattern in PROMPT_INJECTION_PATTERNS):
        codes.append("BALANCING_PROMPT_INJECTION_INPUT")
    if not codes:
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.INPUT_SCHEMA_INVALID,
        message="경제 분석 입력에 허용되지 않는 내용이 포함되어 있습니다.",
        retryable=False,
        validation_codes=list(dict.fromkeys(codes)),
        validation_stage="CONTENT_SAFETY",
    )


def _semantic_input_codes(payload: BalancingPayload) -> list[str]:
    codes: list[str] = []
    for metric in payload.metrics:
        if metric.unit is not METRIC_UNITS[metric.code]:
            codes.append("METRIC_UNIT_MISMATCH")
        if metric.availability is MetricAvailability.AVAILABLE:
            if (
                metric.state is MetricState.NORMAL
                and metric.direction is not MetricDirection.NORMAL
            ):
                codes.append("NORMAL_METRIC_DIRECTION_MISMATCH")
            if (
                metric.state in {MetricState.OBSERVE, MetricState.WARN}
                and metric.direction
                not in {MetricDirection.LOW, MetricDirection.HIGH}
            ):
                codes.append("ALERT_METRIC_DIRECTION_MISMATCH")
        if payload.previous_period is None and metric.previous_value is not None:
            codes.append("PREVIOUS_VALUE_WITHOUT_PERIOD")
        if (
            payload.previous_period is not None
            and metric.availability is MetricAvailability.AVAILABLE
            and metric.previous_value is None
        ):
            codes.append("PREVIOUS_VALUE_REQUIRED")
        if (
            metric.availability is MetricAvailability.UNAVAILABLE
            and metric.previous_value is not None
        ):
            codes.append("UNAVAILABLE_PREVIOUS_VALUE_FORBIDDEN")

    for action in payload.allowed_actions:
        if action.unit is not ACTION_UNITS[action.target_type]:
            codes.append("ACTION_UNIT_MISMATCH")

    available_severities = [
        AnalysisSeverity(metric.state.value)
        for metric in payload.metrics
        if metric.availability is MetricAvailability.AVAILABLE
    ]
    expected_severity = max(
        available_severities,
        key=SEVERITY_PRIORITY.__getitem__,
    )
    if payload.analysis_severity is not expected_severity:
        codes.append("ANALYSIS_SEVERITY_MISMATCH")
    return list(dict.fromkeys(codes))
