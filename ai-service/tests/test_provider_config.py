from __future__ import annotations

import pytest

from ecovillage_ai.config import ProviderRoute, Settings

PROVIDER_ENV_KEYS = (
    "ECOVILLAGE_AI_PROVIDER",
    "ECOVILLAGE_AI_MODEL_ID",
    "ECOVILLAGE_AI_BEDROCK_MODEL_ID",
    "ECOVILLAGE_AI_OPENAI_MODEL_ID",
    "ECOVILLAGE_AI_OPENAI_ENABLED",
    "ECOVILLAGE_AI_ROUTE_BALANCING",
    "ECOVILLAGE_AI_ROUTE_TEACHER_CHAT",
    "ECOVILLAGE_AI_ROUTE_ECONOMIC_EVENT",
    "ECOVILLAGE_AI_ROUTE_QUIZ_DRAFT",
    "OPENAI_API_KEY",
    "AWS_REGION",
    "AWS_DEFAULT_REGION",
)


def _clear_provider_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in PROVIDER_ENV_KEYS:
        monkeypatch.delenv(key, raising=False)


def test_production_defaults_to_bedrock(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_provider_environment(monkeypatch)
    monkeypatch.setenv("ECOVILLAGE_AI_ENVIRONMENT", "production")
    monkeypatch.setenv("ECOVILLAGE_AI_INTERNAL_SERVICE_TOKEN", "production-token")
    monkeypatch.setenv("ECOVILLAGE_AI_BEDROCK_MODEL_ID", "bedrock-model")
    monkeypatch.setenv("AWS_REGION", "ap-northeast-2")

    settings = Settings.from_env()

    assert settings.provider_name == "BEDROCK"
    assert settings.provider_model_id == "bedrock-model"
    assert settings.route_for("teacher-chat") == ProviderRoute(
        "BEDROCK", "bedrock-model"
    )


def test_openai_requires_explicit_opt_in(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_provider_environment(monkeypatch)
    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "OPENAI")
    monkeypatch.setenv("ECOVILLAGE_AI_OPENAI_MODEL_ID", "openai-model")
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")

    with pytest.raises(RuntimeError, match="ECOVILLAGE_AI_OPENAI_ENABLED"):
        Settings.from_env()


def test_openai_requires_api_key_without_exposing_value(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _clear_provider_environment(monkeypatch)
    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "OPENAI")
    monkeypatch.setenv("ECOVILLAGE_AI_OPENAI_MODEL_ID", "openai-model")
    monkeypatch.setenv("ECOVILLAGE_AI_OPENAI_ENABLED", "true")

    with pytest.raises(RuntimeError) as caught:
        Settings.from_env()

    assert str(caught.value) == "OPENAI 사용 시 OPENAI_API_KEY가 필요합니다."


def test_feature_route_can_select_openai_only_when_enabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _clear_provider_environment(monkeypatch)
    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "FAKE")
    monkeypatch.setenv("ECOVILLAGE_AI_ROUTE_TEACHER_CHAT", "OPENAI:openai-model")
    monkeypatch.setenv("ECOVILLAGE_AI_OPENAI_ENABLED", "true")
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")

    settings = Settings.from_env()

    assert settings.route_for("balancing") == ProviderRoute("FAKE", "deterministic-v1")
    assert settings.route_for("teacher-chat") == ProviderRoute(
        "OPENAI", "openai-model"
    )


def test_unknown_provider_is_rejected_before_startup(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _clear_provider_environment(monkeypatch)
    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "UNKNOWN")

    with pytest.raises(ValueError, match="지원하지 않는 AI 공급자"):
        Settings.from_env()


def test_feature_route_requires_explicit_model_id(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _clear_provider_environment(monkeypatch)
    monkeypatch.setenv("ECOVILLAGE_AI_PROVIDER", "FAKE")
    monkeypatch.setenv("ECOVILLAGE_AI_ROUTE_TEACHER_CHAT", "OPENAI")
    monkeypatch.setenv("ECOVILLAGE_AI_OPENAI_MODEL_ID", "openai-model")
    monkeypatch.setenv("ECOVILLAGE_AI_OPENAI_ENABLED", "true")
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")

    with pytest.raises(ValueError, match="공급자:모델ID"):
        Settings.from_env()
