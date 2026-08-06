"""퀴즈 골든셋 실행 스크립트.

`tests/fixtures/quiz_golden_set_v1.json`의 입력 12건을 AI 서비스에 그대로 요청하고,
사람이 채점할 수 있는 형태로 `evals/runs/<run-id>/`에 결과를 남긴다.

실제 공급자를 호출하므로 크레딧을 쓴다. 한 번 실행에 12번 호출한다.

교사 콘솔을 거치지 않고 AI 서비스를 직접 부른다. 대신 백엔드가 하는 두 가지 변환을
그대로 흉내낸다(QuizDraftService 참고).

    concepts   = [topic]
    difficulty = 4:INTRODUCTORY, 5:STANDARD, 6:ADVANCED

실행:
    .venv\\Scripts\\python.exe scripts/run_golden_set.py --run-id run-a-prompt

만들어지는 파일:
    generated.json  요청·응답 원본과 공급자·모델 정보
    questions.md    검토자가 읽을 문항 목록. 모델 이름을 넣지 않아 블라인드 채점에 쓴다
    review-a.json   검토자 A 채점표. 문항 수에 맞춰 비어 있는 칸을 만들어 둔다
    review-b.json   검토자 B 채점표
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from ecovillage_ai.env_file import load_env_file  # noqa: E402

AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
GOLDEN_SET_PATH = AI_SERVICE_ROOT / "tests" / "fixtures" / "quiz_golden_set_v1.json"
RUNS_ROOT = AI_SERVICE_ROOT / "evals" / "runs"
ENDPOINT_PATH = "/internal/v1/generations/quiz-draft"
PROMPT_VERSION = "quiz-draft-v1"
SCHEMA_VERSION = "1.2"
LOCALE = "ko-KR"

# 백엔드 QuizDraftService.difficulty(grade)와 같은 표다. 달라지면 평가 조건이 실제와 어긋난다.
DIFFICULTY_BY_GRADE = {4: "INTRODUCTORY", 5: "STANDARD", 6: "ADVANCED"}

QUESTION_CRITERIA = (
    "answer_correctness",
    "distractor_quality",
    "grade_fit",
    "category_fit",
    "explanation_quality",
)


def build_request(case: dict[str, Any], deadline_seconds: int) -> dict[str, Any]:
    grade = case["grade"]
    difficulty = DIFFICULTY_BY_GRADE[grade]
    expected = case.get("expected_difficulty")
    if expected is not None and expected != difficulty:
        raise ValueError(
            f"{case['case_id']}의 expected_difficulty({expected})가 "
            f"학년 {grade}의 실제 난이도({difficulty})와 다릅니다."
        )
    deadline = datetime.now(UTC) + timedelta(seconds=deadline_seconds)
    return {
        "ai_run_id": str(uuid.uuid4()),
        "request_id": str(uuid.uuid4()),
        "correlation_id": str(uuid.uuid4()),
        "schema_version": SCHEMA_VERSION,
        "prompt_version": PROMPT_VERSION,
        "attempt": 1,
        "deadline_at": deadline.isoformat().replace("+00:00", "Z"),
        "locale": LOCALE,
        "grade": grade,
        "payload": {
            "topic": case["topic"],
            "difficulty": difficulty,
            # 백엔드가 주제 하나를 그대로 개념으로 쓴다. 평가도 같은 조건이어야 한다.
            "concepts": [case["topic"]],
            "learning_objectives": [case["learning_objective"]],
            "category": case["content_type"],
            "question_type": case["question_type"],
            "count": case["question_count"],
        },
    }


def call_ai_service(
    base_url: str, token: str, body: dict[str, Any], timeout: float
) -> tuple[dict[str, Any] | None, str | None]:
    """응답 본문과 오류 메시지를 함께 돌려준다. 실패해도 예외를 올리지 않는다."""
    request = urllib.request.Request(
        base_url.rstrip("/") + ENDPOINT_PATH,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
            "X-Correlation-Id": body["correlation_id"],
            "X-AI-Run-Id": body["ai_run_id"],
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8")), None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        return None, f"HTTP {error.code}: {detail[:500]}"
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        return None, f"{type(error).__name__}: {error}"


def run_case(
    case: dict[str, Any],
    *,
    base_url: str,
    token: str,
    timeout: float,
    deadline_seconds: int,
) -> dict[str, Any]:
    case_id = case["case_id"]
    try:
        body = build_request(case, deadline_seconds)
    except ValueError as error:
        print(f"  [SKIP] {case_id}: {error}")
        return {"case_id": case_id, "status": "SKIPPED", "error": str(error)}

    started = datetime.now(UTC)
    response, error = call_ai_service(base_url, token, body, timeout)
    elapsed_ms = int((datetime.now(UTC) - started).total_seconds() * 1000)

    if response is None:
        print(f"  [FAIL] {case_id} ({elapsed_ms}ms) {error}")
        return {
            "case_id": case_id,
            "status": "FAILED",
            "elapsed_ms": elapsed_ms,
            "request": body,
            "error": error,
        }

    questions = response.get("output", {}).get("questions", [])
    print(f"  [OK]   {case_id} ({elapsed_ms}ms) 문항 {len(questions)}개")
    return {
        "case_id": case_id,
        "status": "OK",
        "elapsed_ms": elapsed_ms,
        "request": body,
        "response": response,
    }


def render_questions_markdown(golden: dict[str, Any], results: list[dict[str, Any]]) -> str:
    """검토자가 읽을 문항 목록을 만든다. 공급자·모델 이름은 일부러 넣지 않는다."""
    case_by_id = {case["case_id"]: case for case in golden["cases"]}
    lines = [
        "# 골든셋 생성 문항",
        "",
        "채점 전에 `quiz_golden_set_v1.json`의 anchors를 먼저 읽으세요.",
        "어떤 모델로 만든 결과인지는 일부러 적지 않았습니다.",
        "",
    ]
    for result in results:
        case = case_by_id[result["case_id"]]
        lines.append(f"## {case['case_id']}")
        lines.append(
            f"{case['grade']}학년 · {case['content_type']} · {case['question_type']} · "
            f"주제 {case['topic']}"
        )
        lines.append(f"학습목표: {case['learning_objective']}")
        lines.append("")

        if result["status"] != "OK":
            lines.append(f"> 생성 실패: {result.get('error', '알 수 없는 오류')}")
            lines.append("")
            continue

        questions = result["response"]["output"]["questions"]
        for index, question in enumerate(questions, start=1):
            lines.append(f"### {index}번")
            lines.append(question["question"])
            lines.append("")
            for choice in question["choices"]:
                marker = "*" if choice["choice_key"] == question["correct_choice_key"] else " "
                lines.append(f"- {marker} {choice['choice_key']}. {choice['text']}")
            lines.append("")
            lines.append(f"해설: {question['explanation']}")
            tags = ", ".join(question["concept_tags"])
            lines.append(f"난이도: {question['difficulty']} · 개념: {tags}")
            lines.append("")
    lines.append("`*` 표시가 정답입니다.")
    lines.append("")
    return "\n".join(lines)


def build_review_template(
    run_id: str, golden: dict[str, Any], results: list[dict[str, Any]], reviewer: str
) -> dict[str, Any]:
    cases = []
    for result in results:
        if result["status"] == "OK":
            count = len(result["response"]["output"]["questions"])
        else:
            count = 0
        cases.append(
            {
                "case_id": result["case_id"],
                "variety": None,
                "questions": [
                    {
                        "index": index,
                        **{criterion: None for criterion in QUESTION_CRITERIA},
                        "veto_wrong_answer": False,
                        "veto_verbatim_reuse": False,
                        "comment": "",
                    }
                    for index in range(1, count + 1)
                ],
            }
        )
    return {
        "run_id": run_id,
        "golden_set_version": golden["golden_set_version"],
        "reviewer": reviewer,
        "reviewed_at": "",
        "note": (
            "점수는 0, 1, 2 정수만 쓴다. 채점 전에 anchors를 먼저 읽는다. "
            "다른 검토자와 상의하지 않는다. questions.md만 보고 채점한다."
        ),
        "banned_verbatim_answers": golden.get("banned_verbatim_answers", []),
        "cases": cases,
    }


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="퀴즈 골든셋 12건을 생성하고 채점표를 만든다")
    parser.add_argument("--run-id", required=True, help="실행 이름. 예: run-a-prompt")
    parser.add_argument("--base-url", default="http://localhost:8000", help="AI 서비스 주소")
    parser.add_argument("--token", help="내부 서비스 토큰. 없으면 .env에서 읽는다")
    parser.add_argument("--case", action="append", help="특정 case_id만 실행한다. 반복 지정 가능")
    parser.add_argument("--workers", type=int, default=4, help="동시에 보낼 요청 수")
    parser.add_argument("--timeout", type=float, default=120.0, help="한 요청의 HTTP 제한시간(초)")
    parser.add_argument(
        "--deadline-seconds", type=int, default=180, help="요청 deadline_at까지의 여유(초)"
    )
    parser.add_argument("--force", action="store_true", help="이미 있는 실행 폴더를 덮어쓴다")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    load_env_file()

    import os

    token = args.token or os.getenv(
        "ECOVILLAGE_AI_INTERNAL_SERVICE_TOKEN", "local-development-token"
    )

    golden = json.loads(GOLDEN_SET_PATH.read_text(encoding="utf-8"))
    cases = golden["cases"]
    if args.case:
        wanted = set(args.case)
        unknown = wanted - {case["case_id"] for case in cases}
        if unknown:
            print(f"모르는 case_id입니다: {', '.join(sorted(unknown))}")
            return 1
        cases = [case for case in cases if case["case_id"] in wanted]

    out_dir = RUNS_ROOT / args.run_id
    if out_dir.exists() and any(out_dir.iterdir()) and not args.force:
        print(f"이미 결과가 있는 폴더입니다: {out_dir}")
        print("덮어쓰려면 --force를 쓰세요.")
        return 1
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"골든셋 {len(cases)}건을 {args.base_url}에 요청합니다. 실제 공급자 크레딧을 씁니다.")
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(
            pool.map(
                lambda case: run_case(
                    case,
                    base_url=args.base_url,
                    token=token,
                    timeout=args.timeout,
                    deadline_seconds=args.deadline_seconds,
                ),
                cases,
            )
        )
    # 동시 실행이라 끝난 순서가 섞인다. 골든셋 차례대로 다시 세운다.
    order = {case["case_id"]: index for index, case in enumerate(cases)}
    results.sort(key=lambda result: order[result["case_id"]])

    succeeded = [result for result in results if result["status"] == "OK"]
    question_count = sum(
        len(result["response"]["output"]["questions"]) for result in succeeded
    )
    metadata = succeeded[0]["response"] if succeeded else {}

    write_json(
        out_dir / "generated.json",
        {
            "run_id": args.run_id,
            "golden_set_version": golden["golden_set_version"],
            "generated_at": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
            "base_url": args.base_url,
            "provider": metadata.get("provider"),
            "model_id": metadata.get("model_id"),
            "prompt_version": metadata.get("prompt_version", PROMPT_VERSION),
            "case_count": len(results),
            "succeeded_count": len(succeeded),
            "question_count": question_count,
            "cases": results,
        },
    )
    (out_dir / "questions.md").write_text(
        render_questions_markdown(golden, results), encoding="utf-8"
    )
    for reviewer, filename in (("A", "review-a.json"), ("B", "review-b.json")):
        write_json(
            out_dir / filename,
            build_review_template(args.run_id, golden, results, reviewer),
        )

    print()
    print(f"성공 {len(succeeded)}/{len(results)}건, 문항 {question_count}개")
    print(f"공급자 {metadata.get('provider')} / 모델 {metadata.get('model_id')}")
    print(f"결과 폴더: {out_dir}")
    print("검토자는 questions.md를 보고 review-a.json, review-b.json을 채웁니다.")
    return 0 if len(succeeded) == len(results) else 1


if __name__ == "__main__":  # pragma: no cover - script entry point
    raise SystemExit(main())
