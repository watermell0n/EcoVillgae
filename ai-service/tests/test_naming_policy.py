from __future__ import annotations

from pathlib import Path

from ecovillage_ai.config import Settings

REPOSITORY_ROOT = Path(__file__).parents[2]
EXCLUDED_PARTS = {".git", ".venv", "node_modules", "__pycache__", ".pnpm-store"}
TEXT_SUFFIXES = {
    ".env",
    ".example",
    ".gradle",
    ".java",
    ".js",
    ".json",
    ".kts",
    ".md",
    ".mjs",
    ".properties",
    ".ps1",
    ".py",
    ".sh",
    ".sql",
    ".toml",
    ".ts",
    ".vue",
    ".yaml",
    ".yml",
}


def test_obsolete_demo_name_is_absent_from_repository() -> None:
    obsolete_name = "bi" + "bim"
    path_matches: list[str] = []
    content_matches: list[str] = []

    for path in REPOSITORY_ROOT.rglob("*"):
        relative_path = path.relative_to(REPOSITORY_ROOT)
        if any(part in EXCLUDED_PARTS for part in relative_path.parts):
            continue
        if obsolete_name in relative_path.as_posix().casefold():
            path_matches.append(relative_path.as_posix())
        if not path.is_file():
            continue
        if path.suffix.casefold() not in TEXT_SUFFIXES and path.name != "Dockerfile":
            continue
        if obsolete_name in path.read_text(encoding="utf-8").casefold():
            content_matches.append(relative_path.as_posix())

    assert path_matches == []
    assert content_matches == []


def test_settings_read_ecovillage_environment_prefix(monkeypatch: object) -> None:
    monkeypatch.setenv("ECOVILLAGE_AI_ENVIRONMENT", "test-prefix")
    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "FAKE")
    monkeypatch.setenv("ECOVILLAGE_AI_DOCS_ENABLED", "false")

    settings = Settings.from_env()

    assert settings.environment == "test-prefix"
    assert settings.provider_name == "FAKE"
    assert settings.docs_enabled is False
