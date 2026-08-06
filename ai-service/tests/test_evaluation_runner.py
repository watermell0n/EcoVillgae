from __future__ import annotations

import json
from collections.abc import Sequence
from pathlib import Path
from typing import Any

import pytest

from ecovillage_ai.evaluation import (
    AI_SERVICE_ROOT,
    HUMAN_CRITERIA,
    CommandCheck,
    evaluate_human_review,
    load_release_manifest,
    run_evaluation,
    validate_evaluation_catalogs,
)
from ecovillage_ai.prompting import SUPPORTED_PROMPT_VERSIONS


def _scores(value: int) -> dict[str, int]:
    return {criterion: value for criterion in HUMAN_CRITERIA}


def _write_review(path: Path, scores: list[tuple[int, int]]) -> None:
    document: dict[str, Any] = {
        "review_version": "human-review-v1",
        "provider": "TEST",
        "model_id": "test-model",
        "prompt_versions": list(SUPPORTED_PROMPT_VERSIONS),
        "cases": [
            {
                "case_id": f"case-{index}",
                "reviews": [
                    {"reviewer_id": "reviewer-a", **_scores(first)},
                    {"reviewer_id": "reviewer-b", **_scores(second)},
                ],
            }
            for index, (first, second) in enumerate(scores, start=1)
        ],
    }
    path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")


def test_release_manifest_has_exactly_350_versioned_evaluation_cases() -> None:
    manifest = load_release_manifest()
    result = validate_evaluation_catalogs(manifest)

    assert result["status"] == "PASS"
    assert result["total_cases"] == 350
    assert result["suite_cases"] == {
        "common": 60,
        "setup-precheck": 48,
        "balancing": 60,
        "teacher-chat": 60,
        "economic-event": 42,
        "investment-news": 32,
        "quiz-draft": 48,
    }
    # 평가 세트는 위 7개 스위트로 고정되어 있고, 평가 세트가 아직 없는 기능(투자 기업 생성)의
    # 프롬프트도 서비스에는 실려 있다. 그래서 완전 일치가 아니라 부분집합으로 본다.
    assert set(result["prompt_versions"]) <= set(SUPPORTED_PROMPT_VERSIONS)


def test_human_review_uses_lower_reviewer_scores_and_passes_gate(
    tmp_path: Path,
) -> None:
    review_path = tmp_path / "human-review.json"
    _write_review(review_path, [(2, 2)] * 12 + [(2, 1)] * 8)

    result = evaluate_human_review(review_path)

    assert result["status"] == "PASS"
    assert result["case_count"] == 20
    assert result["average_score_milli"] == 1600


def test_human_review_fails_when_minimum_score_rate_is_below_gate(
    tmp_path: Path,
) -> None:
    review_path = tmp_path / "human-review.json"
    _write_review(review_path, [(2, 2)] * 18 + [(0, 2), (0, 2)])

    result = evaluate_human_review(review_path)

    assert result["status"] == "FAIL"
    assert min(result["criterion_pass_rates_bps"].values()) == 9000


def test_fake_mvp_run_separates_optional_release_checks(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    commands: list[list[str]] = []

    def passing_runner(command: Sequence[str], cwd: Path) -> CommandCheck:
        assert cwd == AI_SERVICE_ROOT
        commands.append(list(command))
        return CommandCheck("PASS", list(command), 0, 1, ["ok"])

    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "FAKE")
    monkeypatch.setenv("ECOVILLAGE_AI_MODEL_ID", "deterministic-v1")
    report = run_evaluation(
        suite="common",
        case_id=None,
        provider_smoke=False,
        docker_smoke=False,
        human_review_path=None,
        command_runner=passing_runner,
    )

    assert report["status"] == "PASS"
    assert report["selected_evaluation_cases"] == 60
    assert report["automated_gates"]["status"] == "SKIPPED"
    assert report["checks"]["provider_smoke"]["status"] == "SKIPPED"
    assert report["checks"]["docker_smoke"]["status"] == "SKIPPED"
    assert report["checks"]["human_review"]["status"] == "NOT_REVIEWED"
    assert report["production_release_ready"] is False
    assert len(commands) == 2


def test_failed_automated_check_fails_release_report(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def failing_static(command: Sequence[str], cwd: Path) -> CommandCheck:
        status = "FAIL" if "ruff" in command else "PASS"
        return CommandCheck(status, list(command), 1 if status == "FAIL" else 0, 1, [])

    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "FAKE")
    monkeypatch.setenv("ECOVILLAGE_AI_MODEL_ID", "deterministic-v1")
    report = run_evaluation(
        suite="all",
        case_id=None,
        provider_smoke=False,
        docker_smoke=False,
        human_review_path=None,
        command_runner=failing_static,
    )

    assert report["status"] == "FAIL"
    assert report["automated_gates"]["status"] == "FAIL"


def test_single_case_is_appended_to_selected_pytest_node(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    commands: list[list[str]] = []

    def passing_runner(command: Sequence[str], cwd: Path) -> CommandCheck:
        commands.append(list(command))
        return CommandCheck("PASS", list(command), 0, 1, [])

    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "FAKE")
    report = run_evaluation(
        suite="common",
        case_id="teacher-chat-success",
        provider_smoke=False,
        docker_smoke=False,
        human_review_path=None,
        command_runner=passing_runner,
    )

    assert report["selected_evaluation_cases"] == 1
    assert report["case_id"] == "teacher-chat-success"
    assert (
        "tests/test_common_evaluation.py::test_common_evaluation_set"
        "[teacher-chat-success]"
    ) in commands[0]
