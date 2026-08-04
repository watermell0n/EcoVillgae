from __future__ import annotations

import json
from collections.abc import Callable
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient

from tests.factories import (
    auto_monitor_request,
    balancing_request,
    economic_event_request,
    headers,
    investment_news_request,
    monitor_event_request,
    quiz_draft_request,
    quiz_nonsense_ox_request,
    setup_no_reward_request,
    setup_precheck_request,
    teacher_chat_request,
)

FIXTURE_CATALOG_PATH = Path(__file__).parent / "fixtures" / "contract_v1_2_cases.json"
CATALOG = json.loads(FIXTURE_CATALOG_PATH.read_text(encoding="utf-8"))

RequestFactory = Callable[[], dict[str, Any]]

FACTORIES: dict[str, RequestFactory] = {
    "setup_classroom": lambda: setup_precheck_request("CLASSROOM_SETUP"),
    "setup_module_activation": lambda: setup_precheck_request("MODULE_ACTIVATION"),
    "setup_product": lambda: setup_precheck_request("PRODUCT_DRAFT"),
    "setup_quiz_game": lambda: setup_precheck_request("QUIZ_SESSION_DRAFT"),
    "setup_quiz_test_no_reward": setup_no_reward_request,
    "balancing_on_demand": balancing_request,
    "balancing_auto_monitor": auto_monitor_request,
    "teacher_chat": teacher_chat_request,
    "event_teacher": economic_event_request,
    "event_monitor": monitor_event_request,
    "investment_news": investment_news_request,
    "quiz_economy_multiple_choice": quiz_draft_request,
    "quiz_nonsense_ox": quiz_nonsense_ox_request,
}


def _mutate_invalid(case_id: str, body: dict[str, Any]) -> None:
    if case_id == "setup-classroom-candidate":
        body["payload"]["context"]["context_type"] = "CLASSROOM_SETUP"
        body["payload"]["context"]["module_code"] = None
        body["payload"]["context"]["module_id"] = None
    elif case_id == "setup-module-missing-id":
        body["payload"]["context"]["module_id"] = None
    elif case_id == "setup-unrelated-target":
        body["payload"]["candidates"][0]["target_type"] = "ROLE_SALARY"
    elif case_id == "setup-test-reward":
        body["payload"]["context"]["quiz_mode"] = "TEST"
    elif case_id == "auto-monitor-missing-previous-period":
        body["payload"]["previous_period"] = None
    elif case_id == "auto-monitor-normal-model-call":
        body["payload"]["analysis_severity"] = "NORMAL"
    elif case_id == "monitor-event-missing-reference":
        body["payload"]["source_type"] = "MONITOR_RECOMMENDATION"
    elif case_id == "investment-future-price-fact":
        body["payload"]["investment_news_context"]["public_price_facts"].append(
            {
                "step_index": 3,
                "price": 120,
                "change_rate_bps": 1429,
                "revealed_at": "2026-07-15T00:00:00+00:00",
            }
        )
    elif case_id == "quiz-missing-concepts":
        body["payload"]["concepts"] = []
    else:  # pragma: no cover - catalog and test must be extended together
        raise AssertionError(f"Unknown invalid fixture: {case_id}")


def test_fixture_catalog_is_complete_and_unique() -> None:
    cases = [*CATALOG["success"], *CATALOG["invalid"]]

    assert CATALOG["schema_version"] == "1.2"
    assert len(CATALOG["success"]) == 13
    assert len(CATALOG["invalid"]) == 9
    assert len({case["id"] for case in cases}) == len(cases)
    assert {case["factory"] for case in cases} <= FACTORIES.keys()


@pytest.mark.parametrize("case", CATALOG["success"], ids=lambda case: case["id"])
def test_success_fixtures_follow_v1_2_contract(
    client: TestClient, case: dict[str, Any]
) -> None:
    response = client.post(
        case["endpoint"], headers=headers(), json=FACTORIES[case["factory"]]()
    )

    assert response.status_code == 200, response.text
    response_body = response.json()
    assert response_body["schema_version"] == CATALOG["schema_version"]
    assert set(case["expected_output_fields"]) <= response_body["output"].keys()


@pytest.mark.parametrize("case", CATALOG["invalid"], ids=lambda case: case["id"])
def test_invalid_fixtures_are_rejected(
    client: TestClient, case: dict[str, Any]
) -> None:
    request_body = FACTORIES[case["factory"]]()
    _mutate_invalid(case["id"], request_body)

    response = client.post(case["endpoint"], headers=headers(), json=request_body)

    assert response.status_code == case["expected_status"]
    assert response.json()["error"]["code"] == case["expected_error"]
