"""직접 실행할 때 `.env`를 읽어 환경변수를 채운다.

Docker Compose는 `.env`를 스스로 읽어 컨테이너 환경변수로 치환한다. 반면 서비스
코드는 `os.environ`만 보므로, `uvicorn`으로 직접 띄우면 같은 `.env`가 반영되지 않고
공급자가 기본값 `FAKE`로 떨어진다. 이 모듈이 그 차이를 없앤다.

규칙은 세 가지다.

- 이미 지정된 환경변수는 덮어쓰지 않는다. 셸과 컨테이너 설정이 항상 우선한다.
- `ECOVILLAGE_AI_ENV_FILE`로 읽을 파일 경로를 직접 지정할 수 있다.
- `ECOVILLAGE_AI_SKIP_ENV_FILE=true`면 파일을 읽지 않는다.
"""

from __future__ import annotations

import os
from collections.abc import MutableMapping, Sequence
from pathlib import Path

SKIP_FLAG_ENV_KEY = "ECOVILLAGE_AI_SKIP_ENV_FILE"
ENV_FILE_PATH_ENV_KEY = "ECOVILLAGE_AI_ENV_FILE"

BYTE_ORDER_MARK = "\ufeff"

_AI_SERVICE_ROOT = Path(__file__).resolve().parents[2]

# 저장소 루트 파일이 Compose가 쓰는 파일과 같다. ai-service 전용 파일은 재정의용이다.
DEFAULT_ENV_FILE_CANDIDATES: tuple[Path, ...] = (
    _AI_SERVICE_ROOT.parent / ".env",
    _AI_SERVICE_ROOT / ".env",
)


def _is_truthy(value: str | None) -> bool:
    return (value or "").strip().lower() in {"1", "true", "yes"}


def parse_env_file(text: str) -> dict[str, str]:
    """`KEY=VALUE` 줄만 추려 사전으로 만든다. 주석과 빈 줄은 건너뛴다."""
    values: dict[str, str] = {}
    # BOM은 공백이 아니라서 strip으로 지워지지 않는다. 첫 키 이름이 깨지지 않게 먼저 뗀다.
    for raw_line in text.removeprefix(BYTE_ORDER_MARK).splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line.removeprefix("export ").strip()
        key, separator, value = line.partition("=")
        key = key.strip()
        if not separator or not key:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        values[key] = value
    return values


def load_env_file(
    *,
    environment: MutableMapping[str, str] | None = None,
    candidates: Sequence[Path] | None = None,
) -> Path | None:
    """찾은 첫 `.env`를 환경변수에 채우고 그 경로를 돌려준다.

    파일이 없거나 건너뛰기로 지정되면 `None`을 돌려준다.
    """
    target = environment if environment is not None else os.environ
    if _is_truthy(target.get(SKIP_FLAG_ENV_KEY)):
        return None

    override = (target.get(ENV_FILE_PATH_ENV_KEY) or "").strip()
    search: Sequence[Path] = (
        (Path(override),) if override else (candidates or DEFAULT_ENV_FILE_CANDIDATES)
    )
    for candidate in search:
        if not candidate.is_file():
            continue
        for key, value in parse_env_file(
            candidate.read_text(encoding="utf-8-sig")
        ).items():
            target.setdefault(key, value)
        return candidate
    return None
