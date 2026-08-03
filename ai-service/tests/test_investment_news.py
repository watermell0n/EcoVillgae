from __future__ import annotations

import json
from copy import deepcopy
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient

from ecovillage_ai.config import Settings
from ecovillage_ai.main import create_app
from ecovillage_ai.provider import FakeGenerationProvider
from ecovillage_ai.runtime import GenerationCommand, ProviderResult
from tests.factories import headers, investment_news_request

FIXTURE_PATH = Path(__file__).parent / "fixtures" / "investment_news_evaluation_v1.json"
CATALOG = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))


class CountingInvestmentNewsProvider(FakeGenerationProvider):
    def __init__(self, mutation: str | None = None) -> None:
        super().__init__()
        self.mutation = mutation
        self.calls = 0

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        self.calls += 1
        result = await super().generate(command)
        if self.mutation is None:
            return result
        output = json.loads(result.content)
        asset_name = output["investment_news_binding"]["asset_name"]
        mutations = {
            "COMPANY": f"{asset_name}을 운영하는 새 회사의 매출 소식입니다.",
            "PRICE": f"{asset_name} 가격은 백이십 원으로 정해졌습니다.",
            "FUTURE": f"{asset_name}은 다음 단계에 오를 전망입니다.",
            "TRADING_ADVICE": f"지금 {asset_name}을 매수하면 수익을 보장합니다.",
            "REVEALED": f"{asset_name} 뉴스는 학생에게 공개됐습니다.",
        }
        output["narrative"] = mutations[self.mutation]
        return ProviderResult(content=output, usage=result.usage)


def _client(settings: Settings, provider: CountingInvestmentNewsProvider) -> TestClient:
    return TestClient(create_app(settings=settings, provider=provider))


def _request_for_asset(asset: dict[str, Any], target_step: int = 2) -> dict[str, Any]:
    body = investment_news_request()
    context = body["payload"]["investment_news_context"]
    context["scenario_version_id"] = asset["scenario_version_id"]
    context["asset_id"] = asset["asset_id"]
    context["asset_name"] = asset["asset_name"]
    context["asset_symbol"] = asset["asset_symbol"]
    context["price_step_id"] = asset["steps"][target_step - 1]["price_step_id"]
    context["current_step_index"] = target_step
    context["public_price_facts"] = [
        {
            "step_index": index,
            "price": step["price"],
            "change_rate_bps": step["change_rate_bps"],
            "revealed_at": f"2026-07-{index:02d}T00:00:00+00:00",
        }
        for index, step in enumerate(asset["steps"][:target_step], start=1)
    ]
    return body


def _evaluation_cases() -> list[dict[str, Any]]:
    cases = [
        {"asset": deepcopy(asset), "scenario": deepcopy(scenario)}
        for asset in CATALOG["assets"]
        for scenario in CATALOG["scenarios"]
    ]
    assert CATALOG["evaluation_set_version"] == "investment-news-evaluation-v1"
    assert CATALOG["prompt_version"] == "economic-event-v1"
    assert len(cases) == CATALOG["expected_case_count"] == 32
    assert (
        sum(case["scenario"]["kind"] in {"SUCCESS", "BATCH_SUCCESS"} for case in cases)
        == CATALOG["expected_success_case_count"]
        == 8
    )
    assert (
        sum(case["scenario"]["kind"] == "PRE_PROVIDER" for case in cases)
        == CATALOG["expected_pre_provider_rejection_count"]
        == 4
    )
    assert (
        sum(case["scenario"]["kind"] == "INVALID_OUTPUT" for case in cases)
        == CATALOG["expected_invalid_output_case_count"]
        == 20
    )
    return cases


@pytest.mark.parametrize(
    "case",
    _evaluation_cases(),
    ids=lambda case: f"{case['asset']['asset_symbol']}-{case['scenario']['id']}",
)
def test_investment_news_evaluation_set(
    settings: Settings, case: dict[str, Any]
) -> None:
    asset = case["asset"]
    scenario = case["scenario"]
    provider = CountingInvestmentNewsProvider(scenario.get("mutation"))

    if scenario["kind"] == "BATCH_SUCCESS":
        with _client(settings, provider) as client:
            responses = [
                client.post(
                    "/internal/v1/generations/economic-event",
                    headers=headers(),
                    json=_request_for_asset(asset, target_step),
                )
                for target_step in range(1, len(asset["steps"]) + 1)
            ]
        assert provider.calls == len(asset["steps"])
        for target_step, response in enumerate(responses, start=1):
            assert response.status_code == 200, response.text
            binding = response.json()["output"]["investment_news_binding"]
            assert binding["price_step_id"] == asset["steps"][target_step - 1][
                "price_step_id"
            ]
            assert binding["current_step_index"] == target_step
        return

    body = _request_for_asset(asset)
    if scenario["kind"] == "PRE_PROVIDER":
        future_step = asset["steps"][2]
        body["payload"]["investment_news_context"]["public_price_facts"].append(
            {
                "step_index": 3,
                "price": future_step["price"],
                "change_rate_bps": future_step["change_rate_bps"],
                "revealed_at": "2026-07-03T00:00:00+00:00",
            }
        )

    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/economic-event",
            headers=headers(),
            json=body,
        )

    if scenario["kind"] == "PRE_PROVIDER":
        assert provider.calls == 0
        assert response.status_code == 400
        assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"
        return

    assert provider.calls == 1
    if scenario["kind"] == "INVALID_OUTPUT":
        assert response.status_code == 422
        assert scenario["validation_code"] in response.json()["error"][
            "validation_codes"
        ]
        return

    assert response.status_code == 200, response.text
    output = response.json()["output"]
    binding = output["investment_news_binding"]
    assert output["review_status"] == "REVIEW_REQUIRED"
    assert binding["scenario_version_id"] == asset["scenario_version_id"]
    assert binding["asset_id"] == asset["asset_id"]
    assert binding["asset_name"] == asset["asset_name"]
    assert binding["asset_symbol"] == asset["asset_symbol"]
    assert asset["asset_name"] in f"{output['title']} {output['narrative']}"


@pytest.mark.parametrize("field", ["asset_name", "asset_symbol"])
def test_changed_investment_asset_identity_is_rejected(
    settings: Settings, field: str
) -> None:
    class ChangedIdentityProvider(CountingInvestmentNewsProvider):
        async def generate(self, command: GenerationCommand) -> ProviderResult:
            result = await super().generate(command)
            output = json.loads(result.content)
            output["investment_news_binding"][field] += "X"
            return ProviderResult(content=output, usage=result.usage)

    provider = ChangedIdentityProvider()
    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/economic-event",
            headers=headers(),
            json=investment_news_request(),
        )

    assert response.status_code == 422
    assert "INVESTMENT_NEWS_BINDING_MISMATCH" in response.json()["error"][
        "validation_codes"
    ]


@pytest.mark.parametrize("mutation", ["gap", "time"])
def test_price_fact_sequence_is_rejected_before_provider(
    settings: Settings, mutation: str
) -> None:
    body = investment_news_request()
    facts = body["payload"]["investment_news_context"]["public_price_facts"]
    if mutation == "gap":
        facts[1]["step_index"] = 3
        body["payload"]["investment_news_context"]["current_step_index"] = 3
    else:
        facts[1]["revealed_at"] = facts[0]["revealed_at"]
    provider = CountingInvestmentNewsProvider()
    with _client(settings, provider) as client:
        response = client.post(
            "/internal/v1/generations/economic-event",
            headers=headers(),
            json=body,
        )

    assert provider.calls == 0
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_INPUT_SCHEMA_INVALID"
