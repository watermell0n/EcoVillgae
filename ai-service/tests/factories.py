from __future__ import annotations

from copy import deepcopy
from datetime import UTC, datetime, timedelta
from uuid import UUID

AI_RUN_ID = UUID("5fc425a8-7b5f-4984-a483-afd57ba2e706")
REQUEST_ID = UUID("b4d6c65d-df46-4d4b-a83c-a1c8d95ee8fe")
CORRELATION_ID = UUID("b18b92c5-1273-4bed-832e-4d33bcf85f4f")
TARGET_ID = UUID("9442aa84-dafd-4f28-8dc2-e746658609bd")


def headers(token: str = "test-service-token") -> dict[str, str]:
    return {
        "Authorization": f"Bearer {token}",
        "X-AI-Run-Id": str(AI_RUN_ID),
        "X-Correlation-Id": str(CORRELATION_ID),
    }


def common(prompt_version: str) -> dict[str, object]:
    now = datetime.now(UTC)
    return {
        "ai_run_id": str(AI_RUN_ID),
        "request_id": str(REQUEST_ID),
        "correlation_id": str(CORRELATION_ID),
        "schema_version": "1.0",
        "prompt_version": prompt_version,
        "attempt": 1,
        "deadline_at": (now + timedelta(seconds=30)).isoformat(),
        "locale": "ko-KR",
        "grade": 5,
    }


def period() -> dict[str, str]:
    now = datetime.now(UTC)
    return {
        "from": (now - timedelta(days=14)).isoformat(),
        "to": (now - timedelta(days=7)).isoformat(),
        "data_as_of": now.isoformat(),
    }


def balancing_request() -> dict[str, object]:
    body = common("balancing-v1")
    body["payload"] = {
        "metric_version": "economic-metrics-v1",
        "threshold_version": "economic-thresholds-v1",
        "period": period(),
        "sample": {
            "active_students": 12,
            "related_transactions": 40,
            "status": "SUFFICIENT",
        },
        "metrics": [
            {
                "code": "CONSUMPTION_RATE_BPS",
                "value": 4200,
                "previous_value": 3900,
                "availability": "AVAILABLE",
                "unavailable_reason": None,
                "unit": "RATE_BPS",
                "state": "OBSERVE",
                "direction": "HIGH",
                "subject_label": None,
            }
        ],
        "allowed_actions": [
            {
                "action_id": "product-price-default",
                "target_type": "PRODUCT_PRICE",
                "target_id": str(TARGET_ID),
                "current_value": 100,
                "min_value": 80,
                "max_value": 120,
                "unit": "AMOUNT",
                "label": "기본 상품 가격",
            }
        ],
    }
    return deepcopy(body)


def teacher_chat_request() -> dict[str, object]:
    body = common("teacher-chat-v1")
    body["payload"] = {
        "question": "이번 주 소비 흐름을 설명해 주세요.",
        "period": period(),
        "facts": [
            {
                "fact_id": "consumption-rate",
                "label": "소비율",
                "value": 4200,
                "unit": "RATE_BPS",
            }
        ],
        "recent_policy_changes": [],
    }
    return deepcopy(body)


def economic_event_request() -> dict[str, object]:
    body = common("economic-event-v1")
    body["payload"] = {
        "template_type": "TAX",
        "interest_rate_scope": None,
        "effect_summary": ["규칙 엔진이 계산한 세율 효과를 설명한다."],
        "prohibited_effects": ["학생 계좌 직접 변경"],
        "teacher_context": None,
    }
    return deepcopy(body)


def quiz_draft_request() -> dict[str, object]:
    body = common("quiz-draft-v1")
    body["payload"] = {
        "topic": "합리적인 소비",
        "learning_objectives": ["필요와 욕구를 구분한다."],
        "category": "ECONOMY",
        "question_type": "MULTIPLE_CHOICE",
        "count": 2,
    }
    return deepcopy(body)
