from __future__ import annotations

import os
from dataclasses import dataclass, field

ALL_GENERATION_SCOPES = frozenset(
    {
        "ai:generate:balancing",
        "ai:generate:teacher-chat",
        "ai:generate:economic-event",
        "ai:generate:quiz-draft",
    }
)


def _read_scopes(value: str | None) -> frozenset[str]:
    if value is None:
        return ALL_GENERATION_SCOPES
    return frozenset(scope.strip() for scope in value.split(",") if scope.strip())


@dataclass(frozen=True, slots=True)
class Settings:
    app_name: str = "BIBIM AI Service"
    app_version: str = "0.1.0"
    environment: str = "local"
    internal_service_token: str = "local-development-token"
    internal_service_scopes: frozenset[str] = field(default_factory=lambda: ALL_GENERATION_SCOPES)
    provider_name: str = "FAKE"
    provider_model_id: str = "deterministic-v1"
    provider_timeout_seconds: float = 10.0
    minimum_deadline_remaining_seconds: float = 0.05
    docs_enabled: bool = True

    @classmethod
    def from_env(cls) -> Settings:
        environment = os.getenv("BIBIM_AI_ENVIRONMENT", "local")
        token = os.getenv("BIBIM_AI_INTERNAL_SERVICE_TOKEN", "local-development-token")
        if environment.lower() in {"prod", "production"} and token == "local-development-token":
            raise RuntimeError("운영 환경에서는 내부 서비스 토큰을 명시해야 합니다.")

        return cls(
            environment=environment,
            internal_service_token=token,
            internal_service_scopes=_read_scopes(os.getenv("BIBIM_AI_INTERNAL_SERVICE_SCOPES")),
            provider_name=os.getenv("BIBIM_AI_PROVIDER", "FAKE"),
            provider_model_id=os.getenv("BIBIM_AI_MODEL_ID", "deterministic-v1"),
            provider_timeout_seconds=float(os.getenv("BIBIM_AI_PROVIDER_TIMEOUT_SECONDS", "10.0")),
            minimum_deadline_remaining_seconds=float(
                os.getenv("BIBIM_AI_MINIMUM_DEADLINE_REMAINING_SECONDS", "0.05")
            ),
            docs_enabled=os.getenv("BIBIM_AI_DOCS_ENABLED", "true").lower() in {"1", "true", "yes"},
        )
