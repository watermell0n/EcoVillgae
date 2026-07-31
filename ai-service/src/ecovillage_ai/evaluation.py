from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
from collections.abc import Callable, Sequence
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from ecovillage_ai.config import GENERATION_KINDS, Settings

AI_SERVICE_ROOT = Path(__file__).resolve().parents[2]
FIXTURE_ROOT = AI_SERVICE_ROOT / "tests" / "fixtures"
RELEASE_MANIFEST_PATH = FIXTURE_ROOT / "ai_mvp_release_v1.json"
HUMAN_CRITERIA = (
    "factual_consistency",
    "evidence_traceability",
    "grade_appropriateness",
    "actionability",
    "uncertainty_honesty",
)
STATUS_PASS = "PASS"
STATUS_FAIL = "FAIL"
STATUS_SKIPPED = "SKIPPED"
STATUS_NOT_REVIEWED = "NOT_REVIEWED"


@dataclass(frozen=True, slots=True)
class CommandCheck:
    status: str
    command: list[str]
    return_code: int | None
    duration_ms: int
    summary: list[str]
    reason: str | None = None


CommandRunner = Callable[[Sequence[str], Path], CommandCheck]


def load_release_manifest() -> dict[str, Any]:
    return json.loads(RELEASE_MANIFEST_PATH.read_text(encoding="utf-8"))


def validate_evaluation_catalogs(
    manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    resolved = manifest or load_release_manifest()
    suite_counts: dict[str, int] = {}
    prompt_versions: set[str] = set()
    for suite in resolved["suites"]:
        catalog = json.loads((FIXTURE_ROOT / suite["catalog"]).read_text(encoding="utf-8"))
        actual = _catalog_case_count(suite["count_rule"], catalog)
        expected = suite["expected_cases"]
        if actual != expected:
            raise ValueError(
                f"{suite['id']} 평가 건수가 다릅니다: expected={expected}, actual={actual}"
            )
        suite_counts[suite["id"]] = actual
        if catalog.get("prompt_version"):
            prompt_versions.add(catalog["prompt_version"])
    total = sum(suite_counts.values())
    if total < resolved["minimum_evaluation_cases"]:
        raise ValueError(
            "전체 평가 건수가 최소 기준보다 적습니다: "
            f"minimum={resolved['minimum_evaluation_cases']}, actual={total}"
        )
    if prompt_versions != set(resolved["prompt_versions"]):
        raise ValueError("평가 카탈로그와 출시 프로필의 프롬프트 버전이 다릅니다.")
    return {
        "status": STATUS_PASS,
        "total_cases": total,
        "suite_cases": suite_counts,
        "schema_version": resolved["schema_version"],
        "prompt_versions": sorted(prompt_versions),
    }


def evaluate_human_review(path: Path | None) -> dict[str, Any]:
    if path is None:
        return {
            "status": STATUS_NOT_REVIEWED,
            "case_count": 0,
            "reason": "사람 평가 파일을 지정하지 않았습니다.",
        }
    document = json.loads(path.read_text(encoding="utf-8"))
    cases = document.get("cases", [])
    if not cases:
        return {
            "status": STATUS_FAIL,
            "case_count": 0,
            "reason": "완료된 사람 평가 사례가 없습니다.",
        }
    adjudicated: list[dict[str, int]] = []
    for case in cases:
        reviews = case.get("reviews", [])
        if len(reviews) < 2:
            raise ValueError(f"{case.get('case_id', 'unknown')}에는 검토자 점수 2개가 필요합니다.")
        scores: dict[str, int] = {}
        for criterion in HUMAN_CRITERIA:
            values = [review.get(criterion) for review in reviews]
            if any(not isinstance(value, int) or not 0 <= value <= 2 for value in values):
                raise ValueError(
                    f"{case.get('case_id', 'unknown')}의 {criterion} 점수는 0~2 정수여야 합니다."
                )
            scores[criterion] = min(values)
        adjudicated.append(scores)
    criterion_pass_rates_bps = {
        criterion: sum(scores[criterion] >= 1 for scores in adjudicated)
        * 10000
        // len(adjudicated)
        for criterion in HUMAN_CRITERIA
    }
    values = [score for scores in adjudicated for score in scores.values()]
    average_score_milli = sum(values) * 1000 // len(values)
    passed = (
        min(criterion_pass_rates_bps.values()) >= 9500
        and average_score_milli >= 1600
    )
    return {
        "status": STATUS_PASS if passed else STATUS_FAIL,
        "case_count": len(adjudicated),
        "criterion_pass_rates_bps": criterion_pass_rates_bps,
        "average_score_milli": average_score_milli,
        "provider": document.get("provider"),
        "model_id": document.get("model_id"),
        "prompt_versions": document.get("prompt_versions", []),
    }


def run_evaluation(
    *,
    suite: str,
    case_id: str | None,
    provider_smoke: bool,
    docker_smoke: bool,
    human_review_path: Path | None,
    command_runner: CommandRunner | None = None,
) -> dict[str, Any]:
    manifest = load_release_manifest()
    catalog_result = validate_evaluation_catalogs(manifest)
    suite_by_id = {item["id"]: item for item in manifest["suites"]}
    if suite != "all" and suite not in suite_by_id:
        raise ValueError(f"지원하지 않는 평가 묶음입니다: {suite}")
    if case_id is not None and suite == "all":
        raise ValueError("단일 사례 실행에는 --suite로 평가 묶음을 지정해야 합니다.")
    runner = command_runner or _run_command
    if suite == "all":
        pytest_command = [
            sys.executable,
            "-m",
            "pytest",
            "-q",
            "-m",
            "not provider_smoke",
            "-p",
            "no:cacheprovider",
        ]
        selected_cases = catalog_result["total_cases"]
    else:
        test_node = suite_by_id[suite]["test_node"]
        if case_id is not None:
            test_node = f"{test_node}[{case_id}]"
        pytest_command = [
            sys.executable,
            "-m",
            "pytest",
            test_node,
            "-q",
            "-p",
            "no:cacheprovider",
        ]
        selected_cases = 1 if case_id is not None else catalog_result["suite_cases"][suite]
    regression = runner(pytest_command, AI_SERVICE_ROOT)
    static = runner(
        [sys.executable, "-m", "ruff", "check", "src", "tests"],
        AI_SERVICE_ROOT,
    )
    provider = (
        _run_provider_smoke(runner)
        if provider_smoke
        else _skipped_check("실제 공급자 smoke를 요청하지 않았습니다.")
    )
    docker = (
        _run_docker_smoke()
        if docker_smoke
        else _skipped_check("Docker smoke를 요청하지 않았습니다.")
    )
    human = evaluate_human_review(human_review_path)
    settings = Settings.from_env()
    automated_passed = all(
        check.status == STATUS_PASS for check in (regression, static)
    )
    full_release_evaluation = suite == "all"
    requested_optional_passed = (
        (not provider_smoke or provider.status == STATUS_PASS)
        and (not docker_smoke or docker.status == STATUS_PASS)
        and (human_review_path is None or human["status"] == STATUS_PASS)
    )
    production_ready = (
        full_release_evaluation
        and automated_passed
        and provider.status == STATUS_PASS
        and docker.status == STATUS_PASS
        and human["status"] == STATUS_PASS
    )
    automated_gates = {
        **manifest["automated_gates"],
        "status": (
            STATUS_PASS
            if full_release_evaluation and automated_passed
            else STATUS_FAIL
            if full_release_evaluation
            else STATUS_SKIPPED
        ),
    }
    if not full_release_evaluation:
        automated_gates["reason"] = "기능별 실행에서는 전체 MVP 출시 게이트를 판정하지 않습니다."
    return {
        "release_profile_version": manifest["release_profile_version"],
        "status": (
            STATUS_PASS if automated_passed and requested_optional_passed else STATUS_FAIL
        ),
        "release_scope": "FAKE_AI_MVP" if full_release_evaluation else "SUITE_EVALUATION",
        "production_release_ready": production_ready,
        "suite": suite,
        "case_id": case_id,
        "selected_evaluation_cases": selected_cases,
        "catalogs": catalog_result,
        "runtime": _runtime_summary(settings, manifest["prompt_versions"]),
        "automated_gates": automated_gates,
        "checks": {
            "regression": asdict(regression),
            "static": asdict(static),
            "provider_smoke": asdict(provider),
            "docker_smoke": asdict(docker),
            "human_review": human,
        },
    }


def _catalog_case_count(rule: str, catalog: dict[str, Any]) -> int:
    if rule == "FEATURES_X_SCENARIOS":
        return len(catalog["features"]) * len(catalog["scenarios"])
    if rule == "GRADES_X_CASES":
        return len(catalog["grades"]) * (
            len(catalog["success_cases"]) + len(catalog["invalid_output_cases"])
        )
    if rule == "GRADES_X_CASE_GROUPS":
        return len(catalog["grades"]) * sum(
            len(catalog[key])
            for key in (
                "success_cases",
                "pre_provider_rejection_cases",
                "invalid_output_cases",
            )
        )
    if rule == "GRADES_X_BASE_CASES":
        return len(catalog["grades"]) * len(catalog["base_cases"])
    if rule == "TEMPLATES_X_SCENARIOS":
        return len(catalog["templates"]) * len(catalog["scenarios"])
    if rule == "ASSETS_X_SCENARIOS":
        return len(catalog["assets"]) * len(catalog["scenarios"])
    if rule == "QUIZ_MATRIX":
        success = (
            len(catalog["grades"])
            * len(catalog["success_matrix"]["categories"])
            * len(catalog["success_matrix"]["question_types"])
            * len(catalog["success_matrix"]["difficulties"])
        )
        invalid = len(catalog["grades"]) * len(catalog["invalid_output_cases"])
        return success + invalid
    raise ValueError(f"지원하지 않는 평가 건수 규칙입니다: {rule}")


def _runtime_summary(settings: Settings, prompt_versions: list[str]) -> dict[str, Any]:
    return {
        "schema_version": "1.2",
        "provider_routes": {
            kind: {
                "provider": settings.route_for(kind).provider_name,
                "model_id": settings.route_for(kind).model_id,
            }
            for kind in GENERATION_KINDS
        },
        "prompt_versions": prompt_versions,
    }


def _run_command(command: Sequence[str], cwd: Path) -> CommandCheck:
    started = time.monotonic()
    completed = subprocess.run(
        list(command),
        cwd=cwd,
        capture_output=True,
        check=False,
        encoding="utf-8",
        errors="replace",
        env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
    )
    duration_ms = int((time.monotonic() - started) * 1000)
    output = "\n".join(part for part in (completed.stdout, completed.stderr) if part)
    summary = [line for line in output.splitlines() if line.strip()][-8:]
    return CommandCheck(
        status=STATUS_PASS if completed.returncode == 0 else STATUS_FAIL,
        command=list(command),
        return_code=completed.returncode,
        duration_ms=duration_ms,
        summary=summary,
    )


def _skipped_check(reason: str) -> CommandCheck:
    return CommandCheck(
        status=STATUS_SKIPPED,
        command=[],
        return_code=None,
        duration_ms=0,
        summary=[],
        reason=reason,
    )


def _run_provider_smoke(runner: CommandRunner) -> CommandCheck:
    key = "ECOVILLAGE_AI_RUN_PROVIDER_SMOKE"
    previous = os.environ.get(key)
    os.environ[key] = "true"
    try:
        return runner(
            [
                sys.executable,
                "-m",
                "pytest",
                "tests/test_provider_smoke.py",
                "-m",
                "provider_smoke",
                "-q",
                "-p",
                "no:cacheprovider",
            ],
            AI_SERVICE_ROOT,
        )
    finally:
        if previous is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = previous


def _run_docker_smoke() -> CommandCheck:
    docker = shutil.which("docker")
    if docker is None:
        return CommandCheck(
            status=STATUS_FAIL,
            command=["docker"],
            return_code=None,
            duration_ms=0,
            summary=[],
            reason="Docker 실행 파일을 찾을 수 없습니다.",
        )
    image = "ecovillage-ai:mvp-evaluation"
    container = "ecovillage-ai-mvp-evaluation-smoke"
    port = 18080
    started = time.monotonic()
    commands: list[str] = []
    summary: list[str] = []
    launched = False
    try:
        build = _run_command([docker, "build", "-t", image, "."], AI_SERVICE_ROOT)
        commands.append(" ".join(build.command))
        summary.extend(build.summary)
        if build.status != STATUS_PASS:
            return CommandCheck(
                status=STATUS_FAIL,
                command=commands,
                return_code=build.return_code,
                duration_ms=int((time.monotonic() - started) * 1000),
                summary=summary[-8:],
                reason="Docker 이미지 빌드에 실패했습니다.",
            )
        run = _run_command(
            [
                docker,
                "run",
                "--rm",
                "-d",
                "--name",
                container,
                "-p",
                f"127.0.0.1:{port}:8000",
                "-e",
                "ECOVILLAGE_AI_PROVIDER=FAKE",
                image,
            ],
            AI_SERVICE_ROOT,
        )
        commands.append(" ".join(run.command))
        summary.extend(run.summary)
        if run.status != STATUS_PASS:
            return CommandCheck(
                status=STATUS_FAIL,
                command=commands,
                return_code=run.return_code,
                duration_ms=int((time.monotonic() - started) * 1000),
                summary=summary[-8:],
                reason="Docker 컨테이너 실행에 실패했습니다.",
            )
        launched = True
        health: dict[str, Any] | None = None
        for _ in range(30):
            try:
                with urllib.request.urlopen(
                    f"http://127.0.0.1:{port}/healthz", timeout=1
                ) as response:
                    health = json.load(response)
                break
            except OSError:
                time.sleep(0.5)
        expected_prompts = set(load_release_manifest()["prompt_versions"])
        if (
            health is None
            or health.get("status") != "UP"
            or health.get("schema_version") != "1.2"
            or set(health.get("prompt_versions", [])) != expected_prompts
            or any(
                route.get("provider") != "FAKE"
                or route.get("model_id") != "deterministic-v1"
                for route in health.get("provider_routes", {}).values()
            )
        ):
            return CommandCheck(
                status=STATUS_FAIL,
                command=commands,
                return_code=1,
                duration_ms=int((time.monotonic() - started) * 1000),
                summary=summary[-8:],
                reason="Docker health의 공급자·모델·스키마·프롬프트 정보가 다릅니다.",
            )
        summary.append("Docker health metadata verified")
        return CommandCheck(
            status=STATUS_PASS,
            command=commands,
            return_code=0,
            duration_ms=int((time.monotonic() - started) * 1000),
            summary=summary[-8:],
        )
    finally:
        if launched:
            subprocess.run(
                [docker, "rm", "-f", container],
                cwd=AI_SERVICE_ROOT,
                capture_output=True,
                check=False,
            )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="이코빌리지 AI MVP 통합 평가 실행기")
    parser.add_argument(
        "--suite",
        default="all",
        choices=["all", *[item["id"] for item in load_release_manifest()["suites"]]],
        help="전체 회귀 또는 하나의 기능별 평가 묶음",
    )
    parser.add_argument(
        "--provider-smoke",
        action="store_true",
        help="실제 공급자 smoke를 실행합니다. 자격증명과 환경 설정이 필요합니다.",
    )
    parser.add_argument(
        "--case",
        help="선택한 suite의 pytest 매개변수 ID 한 건만 실행합니다.",
    )
    parser.add_argument(
        "--docker-smoke",
        action="store_true",
        help="Docker 이미지 빌드와 health 검사를 실행합니다.",
    )
    parser.add_argument(
        "--human-review",
        type=Path,
        help="두 명의 검토자 점수가 포함된 사람 평가 JSON 파일",
    )
    parser.add_argument("--report", type=Path, help="JSON 결과를 저장할 경로")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        report = run_evaluation(
            suite=args.suite,
            case_id=args.case,
            provider_smoke=args.provider_smoke,
            docker_smoke=args.docker_smoke,
            human_review_path=args.human_review,
        )
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        report = {
            "release_profile_version": "ai-mvp-release-v1",
            "status": STATUS_FAIL,
            "error": str(error),
        }
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.report is not None:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0 if report["status"] == STATUS_PASS else 1


if __name__ == "__main__":  # pragma: no cover - module entry point
    raise SystemExit(main())
