"""실패한 생성 사례의 원본 출력과 검증 오류를 그대로 보여준다.

실제 크레딧을 사용한다.

실행:
    .venv\\Scripts\\python.exe scripts/gms_diagnose.py teacher-chat economic-event
"""

from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gms_probe import load_env_file  # noqa: E402


async def main(names: list[str]) -> int:
    load_env_file()

    from pydantic import ValidationError

    from ecovillage_ai.config import Settings
    from ecovillage_ai.prompting import PromptRegistry
    from ecovillage_ai.provider import build_generation_provider
    from tests.test_provider_smoke import SMOKE_CASES

    settings = Settings.from_env()
    provider = build_generation_provider(settings)
    registry = PromptRegistry()

    selected = [case for case in SMOKE_CASES if case[0] in names] or SMOKE_CASES
    for case_name, kind, request_model, payload_factory, output_model in selected:
        print("=" * 72)
        print(f"[{case_name}] {output_model.__name__}")
        request = request_model.model_validate(payload_factory())
        command = registry.build_command(kind, request, output_model)
        result = await provider.generate(command)

        try:
            decoded = json.loads(result.content)
        except ValueError as error:
            print(f"JSON 파싱 실패: {error}")
            print(result.content[:2000])
            continue

        print("--- 모델 원본 출력 ---")
        print(json.dumps(decoded, ensure_ascii=False, indent=2)[:2500])

        try:
            output_model.model_validate(decoded)
            print("--- Pydantic 검증: 통과 ---")
        except ValidationError as error:
            print("--- Pydantic 검증 실패 ---")
            for item in error.errors(include_input=False, include_url=False):
                location = ".".join(str(part) for part in item["loc"]) or "(root)"
                print(f"  {location}: {item['msg']}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main(sys.argv[1:])))
