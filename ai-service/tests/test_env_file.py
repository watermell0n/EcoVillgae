from __future__ import annotations

from pathlib import Path

import pytest

from ecovillage_ai.env_file import (
    ENV_FILE_PATH_ENV_KEY,
    SKIP_FLAG_ENV_KEY,
    load_env_file,
    parse_env_file,
)
from ecovillage_ai.main import create_app
from tests.test_provider_config import PROVIDER_ENV_KEYS

SAMPLE_ENV = """\
# 주석은 무시한다.

ECOVILLAGE_AI_PROVIDER=FAKE
export ECOVILLAGE_AI_MODEL_ID=env-file-model
OPENAI_BASE_URL="https://example.test/v1"
빈줄과_등호없는_줄
"""


def _write_env_file(directory: Path, text: str) -> Path:
    path = directory / ".env"
    path.write_text(text, encoding="utf-8")
    return path


def test_parse_env_file_reads_only_key_value_lines() -> None:
    values = parse_env_file(SAMPLE_ENV)

    assert values == {
        "ECOVILLAGE_AI_PROVIDER": "FAKE",
        "ECOVILLAGE_AI_MODEL_ID": "env-file-model",
        "OPENAI_BASE_URL": "https://example.test/v1",
    }


def test_parse_env_file_ignores_byte_order_mark() -> None:
    values = parse_env_file("\ufeffECOVILLAGE_AI_PROVIDER=FAKE")

    assert values == {"ECOVILLAGE_AI_PROVIDER": "FAKE"}


def test_load_env_file_reads_byte_order_mark_file(tmp_path: Path) -> None:
    path = tmp_path / ".env"
    path.write_text("ECOVILLAGE_AI_PROVIDER=FAKE\n", encoding="utf-8-sig")
    environment: dict[str, str] = {}

    assert load_env_file(environment=environment, candidates=(path,)) == path
    assert environment == {"ECOVILLAGE_AI_PROVIDER": "FAKE"}


def test_load_env_file_keeps_existing_environment_values(tmp_path: Path) -> None:
    path = _write_env_file(tmp_path, SAMPLE_ENV)
    environment = {"ECOVILLAGE_AI_PROVIDER": "OPENAI"}

    assert load_env_file(environment=environment, candidates=(path,)) == path
    assert environment["ECOVILLAGE_AI_PROVIDER"] == "OPENAI"
    assert environment["ECOVILLAGE_AI_MODEL_ID"] == "env-file-model"


def test_load_env_file_uses_first_existing_candidate(tmp_path: Path) -> None:
    missing = tmp_path / "missing" / ".env"
    present = _write_env_file(tmp_path, "ECOVILLAGE_AI_MODEL_ID=second\n")
    environment: dict[str, str] = {}

    assert load_env_file(environment=environment, candidates=(missing, present)) == present
    assert environment["ECOVILLAGE_AI_MODEL_ID"] == "second"


def test_load_env_file_returns_none_without_file(tmp_path: Path) -> None:
    environment: dict[str, str] = {}

    assert load_env_file(environment=environment, candidates=(tmp_path / ".env",)) is None
    assert environment == {}


def test_load_env_file_can_be_skipped(tmp_path: Path) -> None:
    path = _write_env_file(tmp_path, SAMPLE_ENV)
    environment = {SKIP_FLAG_ENV_KEY: "true"}

    assert load_env_file(environment=environment, candidates=(path,)) is None
    assert "ECOVILLAGE_AI_MODEL_ID" not in environment


def test_load_env_file_honours_explicit_path(tmp_path: Path) -> None:
    explicit = tmp_path / "custom.env"
    explicit.write_text("ECOVILLAGE_AI_MODEL_ID=custom\n", encoding="utf-8")
    ignored = _write_env_file(tmp_path, "ECOVILLAGE_AI_MODEL_ID=ignored\n")
    environment = {ENV_FILE_PATH_ENV_KEY: str(explicit)}

    assert load_env_file(environment=environment, candidates=(ignored,)) == explicit
    assert environment["ECOVILLAGE_AI_MODEL_ID"] == "custom"


def test_create_app_reads_env_file_when_settings_are_not_given(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Docker 없이 띄워도 .env의 공급자 설정이 반영되어야 한다."""
    for key in PROVIDER_ENV_KEYS:
        monkeypatch.delenv(key, raising=False)
    explicit = tmp_path / "startup.env"
    explicit.write_text(
        "ECOVILLAGE_AI_PROVIDER=FAKE\nECOVILLAGE_AI_MODEL_ID=env-file-model\n",
        encoding="utf-8",
    )
    monkeypatch.setenv(ENV_FILE_PATH_ENV_KEY, str(explicit))

    app = create_app()

    assert app.state.settings.provider_name == "FAKE"
    assert app.state.settings.provider_model_id == "env-file-model"
