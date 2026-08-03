"""GMS 연결 점검 스크립트.

실제 크레딧을 사용한다. 어떤 API 경로와 구조화 출력 방식이 통과하는지 최소 호출로 확인한다.

실행:
    .venv\\Scripts\\python.exe scripts/gms_probe.py

필요한 환경변수:
    OPENAI_BASE_URL, OPENAI_API_KEY, ECOVILLAGE_AI_OPENAI_MODEL_ID
"""

from __future__ import annotations

import asyncio
import json
import os
import sys
import time
from pathlib import Path


def load_env_file() -> Path | None:
    """저장소 루트나 ai-service의 .env를 읽어 환경변수에 넣는다.

    서비스 코드는 .env를 읽지 않는다. Docker Compose만 변수 치환에 사용한다.
    이 스크립트는 직접 실행하므로 여기서 한 번 읽어 준다.
    이미 셸에 설정된 값은 덮어쓰지 않는다.
    """
    for candidate in (
        Path(__file__).resolve().parents[2] / ".env",
        Path(__file__).resolve().parents[1] / ".env",
    ):
        if not candidate.is_file():
            continue
        for raw_line in candidate.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = value
        return candidate
    return None

SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {"type": "string"},
        "grade": {"type": "integer"},
    },
    "required": ["summary", "grade"],
    "additionalProperties": False,
}

INSTRUCTION = "당신은 초등학교 교사를 돕는 도우미다. 반드시 한국어로 답한다."
USER_INPUT = '{"topic": "용돈 관리", "grade": 5}'


async def probe_responses(client, model: str) -> dict:
    """현재 코드가 쓰는 Responses API 경로."""
    started = time.perf_counter()
    response = await client.responses.create(
        model=model,
        instructions=INSTRUCTION,
        input=USER_INPUT,
        text={
            "format": {
                "type": "json_schema",
                "name": "probe_output",
                "strict": True,
                "schema": SCHEMA,
            }
        },
        max_output_tokens=512,
        store=False,
    )
    elapsed = time.perf_counter() - started
    usage = getattr(response, "usage", None)
    return {
        "elapsed_seconds": round(elapsed, 2),
        "input_tokens": getattr(usage, "input_tokens", None),
        "output_tokens": getattr(usage, "output_tokens", None),
        "text": getattr(response, "output_text", None),
    }


async def probe_chat(client, model: str) -> dict:
    """GMS 예시가 쓰는 Chat Completions 경로."""
    started = time.perf_counter()
    response = await client.chat.completions.create(
        model=model,
        messages=[
            {"role": "developer", "content": INSTRUCTION},
            {"role": "user", "content": USER_INPUT},
        ],
        response_format={
            "type": "json_schema",
            "json_schema": {
                "name": "probe_output",
                "strict": True,
                "schema": SCHEMA,
            },
        },
        max_completion_tokens=512,
    )
    elapsed = time.perf_counter() - started
    usage = getattr(response, "usage", None)
    return {
        "elapsed_seconds": round(elapsed, 2),
        "input_tokens": getattr(usage, "prompt_tokens", None),
        "output_tokens": getattr(usage, "completion_tokens", None),
        "text": response.choices[0].message.content,
    }


async def main() -> int:
    env_file = load_env_file()
    print(f".env: {env_file if env_file else '없음 (셸 환경변수를 사용합니다)'}")

    base_url = os.getenv("OPENAI_BASE_URL", "")
    api_key = os.getenv("OPENAI_API_KEY", "")
    model = os.getenv("ECOVILLAGE_AI_OPENAI_MODEL_ID", "")
    missing = [
        name
        for name, value in (
            ("OPENAI_BASE_URL", base_url),
            ("OPENAI_API_KEY", api_key),
            ("ECOVILLAGE_AI_OPENAI_MODEL_ID", model),
        )
        if not value
    ]
    if missing:
        print(f"다음 환경변수가 필요합니다: {', '.join(missing)}")
        return 2

    from openai import AsyncOpenAI

    client = AsyncOpenAI(timeout=30.0, max_retries=0)
    print(f"base_url = {base_url}")
    print(f"model    = {model}\n")

    results: dict[str, object] = {}
    for name, probe in (("responses", probe_responses), ("chat_completions", probe_chat)):
        try:
            outcome = await probe(client, model)
            parsed = None
            try:
                parsed = json.loads(outcome["text"] or "")
            except (TypeError, ValueError):
                pass
            outcome["schema_ok"] = isinstance(parsed, dict) and set(parsed) == {
                "summary",
                "grade",
            }
            results[name] = {"status": "PASS", **outcome}
            print(f"[PASS] {name}")
            print(f"       지연 {outcome['elapsed_seconds']}s, "
                  f"입력 {outcome['input_tokens']} / 출력 {outcome['output_tokens']} 토큰")
            print(f"       strict 스키마 준수: {outcome['schema_ok']}")
            print(f"       응답: {outcome['text']}\n")
        except Exception as error:  # noqa: BLE001 - 점검 목적이라 모든 실패를 표시한다
            results[name] = {"status": "FAIL", "error": f"{type(error).__name__}: {error}"}
            print(f"[FAIL] {name}")
            print(f"       {type(error).__name__}: {error}\n")

    print("=" * 60)
    passed = [name for name, value in results.items() if value.get("status") == "PASS"]
    if "responses" in passed:
        print("결론: Responses API 사용 가능 → 코드 수정 없이 그대로 전환")
    elif "chat_completions" in passed:
        print("결론: Chat Completions만 가능 → OpenAI 어댑터에 chat 경로 추가 필요")
    else:
        print("결론: 두 경로 모두 실패 → 키·base_url·모델 ID를 확인")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
