from __future__ import annotations

from copy import deepcopy
from datetime import UTC, datetime, timedelta
from uuid import UUID

AI_RUN_ID = UUID("5fc425a8-7b5f-4984-a483-afd57ba2e706")
REQUEST_ID = UUID("b4d6c65d-df46-4d4b-a83c-a1c8d95ee8fe")
CORRELATION_ID = UUID("b18b92c5-1273-4bed-832e-4d33bcf85f4f")
TARGET_ID = UUID("9442aa84-dafd-4f28-8dc2-e746658609bd")
SCENARIO_VERSION_ID = UUID("70aa0f06-5bd2-45f0-8faf-c7bbc75e541a")
ASSET_ID = UUID("248e0b3b-4193-435e-b144-28e36861c35a")
PRICE_STEP_ID = UUID("9ff0c109-6847-4ee5-b5b5-ff6824187618")
TARGET_PRICE_STEP_ID = UUID("cc79fc2e-a703-4ac6-84ab-7e1f006e72b4")
SOURCE_PROPOSAL_ID = UUID("a5e5548f-bde5-4724-890c-55fe4581871f")


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
        "schema_version": "1.2",
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
    body["analysis_mode"] = "ON_DEMAND"
    body["trigger_type"] = "TEACHER_REQUEST"
    body["payload"] = {
        "metric_version": "economic-metrics-v1",
        "threshold_version": "economic-thresholds-v1",
        "period": period(),
        "previous_period": None,
        "analysis_severity": "OBSERVE",
        "sample": {
            "active_students": 12,
            "related_transactions": 40,
            "status": "SUFFICIENT",
        },
        "metrics": [
            {
                "code": "CONSUMPTION_RATE_BPS",
                "value": 4200,
                "previous_value": None,
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


def auto_monitor_request() -> dict[str, object]:
    body = balancing_request()
    now = datetime.now(UTC)
    body["analysis_mode"] = "AUTO_MONITOR"
    body["trigger_type"] = "AUTO_MONITOR"
    body["payload"]["period"] = {
        "from": (now - timedelta(days=7)).isoformat(),
        "to": now.isoformat(),
        "data_as_of": now.isoformat(),
    }
    body["payload"]["previous_period"] = {
        "from": (now - timedelta(days=14)).isoformat(),
        "to": (now - timedelta(days=7)).isoformat(),
        "data_as_of": now.isoformat(),
    }
    body["payload"]["analysis_severity"] = "WARN"
    body["payload"]["metrics"][0]["previous_value"] = 3900
    body["payload"]["metrics"][0]["state"] = "WARN"
    return deepcopy(body)


def setup_precheck_request(context_type: str = "MODULE_ACTIVATION") -> dict[str, object]:
    body = common("setup-precheck-v1")
    body["analysis_mode"] = "SETUP_PRECHECK"
    body["trigger_type"] = "TEACHER_REQUEST"
    context = {
        "context_type": context_type,
        "course_scale": "ONE_MONTH",
        "expected_students": 24,
        "operation_period_days": 30,
        "active_modules": [],
        "module_code": None,
        "module_id": None,
        "quiz_mode": None,
    }
    if context_type == "CLASSROOM_SETUP":
        candidates = []
    elif context_type == "MODULE_ACTIVATION":
        context["module_code"] = "ROLE_SALARY"
        context["module_id"] = str(TARGET_ID)
        candidates = [
            {
                "action_id": "salary-default",
                "target_type": "ROLE_SALARY",
                "target_id": str(TARGET_ID),
                "proposed_value": 1000,
                "recommended_min_value": 850,
                "recommended_max_value": 1150,
                "allowed_min_value": 100,
                "allowed_max_value": 2000,
                "unit": "AMOUNT",
                "reason_codes": ["EXPECTED_PURCHASING_POWER"],
                "label": "기본 역할 급여",
            }
        ]
    elif context_type == "PRODUCT_DRAFT":
        candidates = [
            {
                "action_id": "product-price-default",
                "target_type": "PRODUCT_PRICE",
                "target_id": str(TARGET_ID),
                "proposed_value": 300,
                "recommended_min_value": 255,
                "recommended_max_value": 345,
                "allowed_min_value": 1,
                "allowed_max_value": 500,
                "unit": "AMOUNT",
                "reason_codes": ["SALARY_PRICE_BALANCE"],
                "label": "상품 가격",
            }
        ]
    else:
        context["quiz_mode"] = "GAME"
        candidates = [
            {
                "action_id": "quiz-reward-default",
                "target_type": "GAME_REWARD",
                "target_id": str(TARGET_ID),
                "proposed_value": 50,
                "recommended_min_value": 43,
                "recommended_max_value": 58,
                "allowed_min_value": 0,
                "allowed_max_value": 100,
                "unit": "AMOUNT",
                "reason_codes": ["REWARD_ISSUANCE_SHARE"],
                "label": "게임 퀴즈 보상",
            }
        ]
    body["payload"] = {
        "context": context,
        "candidates": candidates,
        "checks": [
            {
                "check_id": "required-values",
                "severity": "PASS",
                "reason_code": "REQUIRED_VALUES_PRESENT",
                "subject_action_ids": [candidate["action_id"] for candidate in candidates],
                "message": "필수 설정값이 준비되었습니다.",
            }
        ],
    }
    return deepcopy(body)


def setup_no_reward_request() -> dict[str, object]:
    body = setup_precheck_request("QUIZ_SESSION_DRAFT")
    body["payload"]["context"]["quiz_mode"] = "TEST"
    body["payload"]["candidates"] = []
    body["payload"]["checks"] = [
        {
            "check_id": "reward-forbidden",
            "severity": "PASS",
            "reason_code": "TEST_REWARD_FORBIDDEN",
            "subject_action_ids": [],
            "message": "TEST에는 화폐 보상을 설정하지 않습니다.",
        }
    ]
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
        "course_scale": "ONE_MONTH",
        "source_type": "TEACHER_REQUEST",
        "source_proposal_id": None,
        "source_evidence_codes": [],
        "template_type": "TAX",
        "interest_rate_scope": None,
        "effect_summary": ["규칙 엔진이 계산한 세율 효과를 설명한다."],
        "prohibited_effects": ["학생 계좌 직접 변경"],
        "teacher_context": None,
        "investment_news_context": None,
    }
    return deepcopy(body)


def monitor_event_request() -> dict[str, object]:
    body = economic_event_request()
    body["payload"]["source_type"] = "MONITOR_RECOMMENDATION"
    body["payload"]["source_proposal_id"] = str(SOURCE_PROPOSAL_ID)
    body["payload"]["source_evidence_codes"] = ["TAX_RECOVERY_WARN"]
    return deepcopy(body)


def investment_news_request() -> dict[str, object]:
    body = economic_event_request()
    body["grade"] = 6
    body["payload"]["template_type"] = "INVESTMENT_NEWS"
    body["payload"]["effect_summary"] = ["현재 공개 가격 단계와 연결된 소식이다."]
    body["payload"]["investment_news_context"] = {
        "scenario_version_id": str(SCENARIO_VERSION_ID),
        "asset_id": str(ASSET_ID),
        "price_step_id": str(PRICE_STEP_ID),
        "target_price_step_id": str(TARGET_PRICE_STEP_ID),
        "asset_name": "푸른별",
        "asset_symbol": "BLUE",
        "fictional_company_profile": "친환경 이동 수단을 만드는 가상의 지역 기업",
        "investment_mode": "LONG_TERM",
        "scenario_source": "HISTORICAL_INDEX",
        "settlement_rule": "COURSE_END_FINAL",
        "current_step_index": 2,
        "target_step_index": 3,
        "target_direction": "FALL",
        "historical_index_reference": {
            "series_code": "SP500",
            "event_name": "급격한 시장 충격과 회복 구간",
            "source_name": "Federal Reserve Bank of St. Louis FRED",
            "source_url": "https://fred.stlouisfed.org/series/SP500",
            "period_start": "2020-02-19",
            "period_end": "2020-08-18",
        },
        "public_price_facts": [
            {
                "step_index": 1,
                "price": 100,
                "change_rate_bps": 0,
                "revealed_at": "2026-07-01T00:00:00+00:00",
            },
            {
                "step_index": 2,
                "price": 105,
                "change_rate_bps": 500,
                "revealed_at": "2026-07-08T00:00:00+00:00",
            },
        ],
    }
    return deepcopy(body)


def quiz_draft_request() -> dict[str, object]:
    body = common("quiz-draft-v1")
    body["payload"] = {
        "topic": "합리적인 소비",
        "difficulty": "STANDARD",
        "concepts": ["필요와 욕구"],
        "learning_objectives": ["필요와 욕구를 구분한다."],
        "category": "ECONOMY",
        "question_type": "MULTIPLE_CHOICE",
        "count": 2,
    }
    return deepcopy(body)


def quiz_nonsense_ox_request() -> dict[str, object]:
    body = quiz_draft_request()
    body["payload"]["topic"] = "말장난"
    body["payload"]["difficulty"] = "INTRODUCTORY"
    body["payload"]["concepts"] = ["언어 유희"]
    body["payload"]["learning_objectives"] = ["안전한 언어 유희를 구분한다."]
    body["payload"]["category"] = "NONSENSE"
    body["payload"]["question_type"] = "OX"
    body["payload"]["count"] = 1
    return deepcopy(body)
